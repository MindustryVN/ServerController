package mindustrytool.workflow;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import arc.files.Fi;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.Log;
import lombok.Getter;
import mindustry.Vars;
import mindustrytool.Config;
import mindustrytool.type.WorkflowContext;
import mindustrytool.utils.JsonUtils;
import mindustrytool.workflow.errors.WorkflowError;
import mindustrytool.workflow.nodes.EventListenerWorkflow;
import mindustrytool.workflow.nodes.IntervalWorkflow;
import mindustrytool.workflow.nodes.MathRandomWorkflow;
import mindustrytool.workflow.nodes.SendChatToPlayerWorkflow;
import mindustrytool.workflow.nodes.SendChatWorkflow;
import mindustrytool.workflow.nodes.WaitWorkflow;

public class Workflow {
    private final HashMap<Object, Seq<Cons2<?, Boolean>>> events = new HashMap<>();

    @Getter
    private final HashMap<String, WorkflowNode> nodeTypes = new HashMap<>();
    @Getter
    private final HashMap<String, WorkflowNode> nodes = new HashMap<>();
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    private final Fi WORKFLOW_DIR = Vars.dataDirectory.child("workflow");
    private final Fi WORKFLOW_FILE = WORKFLOW_DIR.child("workflow.json");
    private final Fi WORKFLOW_DATA_FILE = WORKFLOW_DIR.child("workflow_data.json");

    @Getter
    public WorkflowContext context;

    public void init() {
        try {
            register(new EventListenerWorkflow());
            register(new SendChatToPlayerWorkflow());
            register(new SendChatWorkflow());
            register(new IntervalWorkflow());
            register(new WaitWorkflow());
            register(new MathRandomWorkflow());

            WORKFLOW_DIR.mkdirs();
            WORKFLOW_FILE.file().createNewFile();
            WORKFLOW_DATA_FILE.file().createNewFile();

            loadWorkflowFromFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JsonNode readWorkflowData() {
        return JsonUtils.readJson(WORKFLOW_DATA_FILE.readString());
    }

    public void writeWorkflowData(JsonNode data) {
        WORKFLOW_DATA_FILE.writeString(JsonUtils.toJsonString(data));
    }

    private void loadWorkflowFromFile() {
        String content = WORKFLOW_FILE.readString();
        context = JsonUtils.readJsonAsClass(content, WorkflowContext.class);
        load(context);
    }

    private void writeWorkflowToFile() {
        WORKFLOW_FILE.writeString(JsonUtils.toJsonString(context));
    }

    private void register(WorkflowNode node) {
        if (nodeTypes.containsKey(node.getName())) {
            throw new IllegalStateException("Node already registered: " + node.getName());
        }

        nodeTypes.put(node.getName(), node);
    }

    public void clear() {
        events.clear();
        nodeTypes.clear();
        nodes.clear();

        scheduledTasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel(true);
            }
        });

        scheduledTasks.clear();

        Log.info("Workflow unloaded");
    }

    public void load(WorkflowContext context) {
        Log.info("Load workflow context" + context);

        nodes.values().forEach(node -> node.unload(this));
        nodes.clear();
        events.clear();

        scheduledTasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel(true);
            }
        });
        scheduledTasks.clear();

        this.context = context;
        writeWorkflowToFile();

        for (var data : context.getNodes()) {
            var node = nodeTypes.get(data.getName());

            if (node == null) {
                throw new WorkflowError("Node type not found: " + data.getName());
            }

            var constructors = node.getClass().getConstructors();

            if (constructors == null || constructors.length == 0) {
                throw new WorkflowError("No constructor for node: " + node.getClass().getSimpleName());
            }

            try {
                var newNode = (WorkflowNode) constructors[0].newInstance();

                newNode.setId(data.getId());

                data.getState().getOutputs().entrySet().forEach(entry -> {
                    var name = entry.getKey();
                    var nextId = entry.getValue();
                    var newOutput = newNode.getOutputs()
                            .stream()
                            .filter(nn -> nn.getName().equals(name))
                            .findFirst()
                            .orElseThrow(() -> new WorkflowError(
                                    "Node output not found: " + name + " on node: " + node.getName()));

                    newOutput.setNextId(nextId);
                });

                data.getState().getFields().entrySet().forEach(entry -> {
                    var name = entry.getKey();
                    var value = entry.getValue();
                    var newOutput = newNode.getFields()
                            .stream()
                            .filter(nn -> nn.getName().equals(name))
                            .findFirst()
                            .orElseThrow(() -> new WorkflowError(
                                    "Node fields not found: " + name + " on node: "
                                            + node.getName()));

                    if (newOutput.getConsumer().isRequired() && value.getConsumer() == null) {
                        throw new WorkflowError("Node fields value is required: " + name
                                + " on node: " + node.getName());
                    }

                    newOutput.getConsumer().setValue(value.getConsumer());

                    if (value.getVariableName() != null) {
                        newOutput.getProducer().setVariableName(value.getVariableName());
                    }
                });

                nodes.put(newNode.getId(), newNode);

                newNode.init(this);

                Log.debug("Node loaded: " + newNode);

            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new WorkflowError("Can not create new node: " + node.getClass().getSimpleName(), e);
            }
        }
        Log.info("Context loaded");
    }

    public <T> Cons2<T, Boolean> on(Class<T> type, Cons2<T, Boolean> listener) {
        events.computeIfAbsent(type, (_ignore) -> new Seq<>(Cons2.class)).add(listener);

        return listener;
    }

    public <T> boolean remove(Class<T> type, Cons2<T, Boolean> listener) {
        return events.computeIfAbsent(type, (_ignore) -> new Seq<>(Cons2.class)).remove(listener);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T extends Enum<T>> void fire(Enum<T> type, boolean before) {
        Seq<Cons2<?, Boolean>> listeners = events.get(type);

        if (listeners != null) {
            int len = listeners.size;
            Cons2[] items = listeners.items;
            for (int i = 0; i < len; i++) {
                items[i].get(type, before);
            }
        }
    }

    /** Fires a non-enum event by class. */
    public <T> void fire(T type, boolean before) {
        Log.debug(type.getClass().getName() + " fired: " + type);

        fire(type.getClass(), type, before);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T> void fire(Class<?> ctype, T type, boolean before) {
        Seq<Cons2<?, Boolean>> listeners = events.get(ctype);

        if (listeners != null) {
            int len = listeners.size;
            Cons2[] items = listeners.items;
            for (int i = 0; i < len; i++) {
                items[i].get(type, before);
            }
        }
    }

    public void scheduleAtFixedRate(Runnable runnable, long delay, long period) {
        Log.debug("Schedule task at fixed rate: " + runnable.getClass().getName() + " delay: " + delay
                + " period: " + period);
        scheduledTasks
                .add(Config.BACKGROUND_SCHEDULER.scheduleAtFixedRate(runnable, delay, period, TimeUnit.SECONDS));
    }

    public void scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay) {
        Log.debug("Schedule task with fixed delay: " + runnable.getClass().getName() + " initialDelay: "
                + initialDelay + " delay: " + delay);
        scheduledTasks.add(Config.BACKGROUND_SCHEDULER.scheduleWithFixedDelay(runnable, initialDelay, delay,
                TimeUnit.SECONDS));
    }

    public void schedule(Runnable runnable, long delay) {
        Log.debug("Schedule task: " + runnable.getClass().getName() + " delay: " + delay);
        scheduledTasks.add(Config.BACKGROUND_SCHEDULER.schedule(runnable, delay, TimeUnit.SECONDS));
    }

}
