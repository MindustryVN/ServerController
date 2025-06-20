package mindustrytool.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import arc.util.Log;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import mindustrytool.workflow.errors.WorkflowError;

@Data
@Accessors(chain = true)
public abstract class WorkflowNode {

    // Variable name should able to contain dot(.) to access nested object, a-z A-Z
    // 0-9 _ -
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    @JsonIgnore
    private String id;
    private int inputs = 1;

    protected final String name;
    protected final String group;
    protected final String color;

    protected List<WorkflowField<?, ?>> fields = new ArrayList<>();
    protected List<WorkflowOutput> outputs = new ArrayList<>();

    public void init(Workflow context) {
    }

    public void unload(Workflow context) {
        // Unload logic if needed
    }

    public WorkflowNode(String name, String group, String color, int inputs) {
        this.name = name;
        this.group = group;
        this.color = color;
        this.inputs = inputs;
    }

    public void execute(WorkflowEmitEvent event) {
        event.next(outputs.get(0).getNextId());
    }

    public void defaultOneOutput() {
        new WorkflowOutput("Next", "None");
    }

    public String nextId() {
        if (outputs.isEmpty()) {
            throw new WorkflowError("No outputs defined for node: " + name);
        }

        return outputs.get(0).getNextId();
    }

    @Data
    public class WorkflowOutput {
        private final String name;
        private final String description;

        @JsonIgnore
        private String nextId;

        public WorkflowOutput(String name, String description) {
            this.name = name;
            this.description = description;

            outputs.add(this);
        }
    }

    @Data
    public class WorkflowFieldOption {
        @JsonSerialize
        private final String label;

        @JsonSerialize
        private final String value;

        @JsonSerialize(using = ClassSerializer.class)
        private Class<?> produceType;

        public WorkflowFieldOption(String label, String value, Class<?> produceType) {
            this.label = label;
            this.value = value;
            this.produceType = produceType;
        }

        public WorkflowFieldOption(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    @ToString
    @Setter
    @Getter
    public class FieldProducer<T> {

        @JsonSerialize(using = ClassSerializer.class)
        private Class<?> produceType;
        private String variableName;

        public FieldProducer(String variableName) {
            this.variableName = variableName;
        }
    }

    @Data
    public class FieldConsumer<T> {
        private final Class<T> type;
        private WorkflowUnit unit;
        private boolean required = true;
        private String value;
        private T defaultValue;
        private final List<WorkflowFieldOption> options = new ArrayList<>();

        public FieldConsumer<T> notRequired() {
            this.required = false;
            return this;
        }

        public FieldConsumer<T> unit(WorkflowUnit unit) {
            this.unit = unit;
            return this;
        }

        public Class<?> asClass() {
            try {
                return Class.forName(value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid class: " + value + " " + e.getMessage(), e);
            }
        }

        public Boolean asBoolean() {
            try {

                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid boolean value: " + value + " " + e.getMessage(), e);
            }
        }

        public Long asLong() {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid long value: " + value + " " + e.getMessage(), e);
            }
        }

        @SuppressWarnings("rawtypes")
        public T asEnum() {
            if (!type.isEnum()) {
                throw new WorkflowError("Type is not an enum: " + type.getName());
            }

            try {
                return (T) Enum.valueOf((Class<? extends Enum>) type, value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid enum value: " + value + " for type: " + type.getName(), e);
            }
        }

        public FieldConsumer<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }

        public FieldConsumer<T> option(String name, String value) {
            options.add(new WorkflowFieldOption(name, value));
            return this;
        }

        public FieldConsumer<T> option(String name, String value, Class<?> produceType) {
            options.add(new WorkflowFieldOption(name, value, produceType));
            return this;
        }

        public FieldConsumer<T> options(Class<? extends Enum<?>> enumClass) {
            for (var enumConstant : enumClass.getEnumConstants()) {
                options.add(new WorkflowFieldOption(enumConstant.name(), enumConstant.name()));
            }
            return this;
        }

        public T consume(WorkflowEmitEvent event) {
            var fields = value.split(".");

            if (fields.length == 0) {
                return (T) event.getValues().get(fields[0]);
            }

            Object result = event.getValues().get(fields[0]);

            for (int index = 1; index < fields.length; index++) {
                try {
                    result = result.getClass().getDeclaredField(fields[index]).get(result);
                } catch (IllegalArgumentException //
                        | IllegalAccessException //
                        | NoSuchFieldException
                        | SecurityException e//
                ) {
                    throw new IllegalStateException(
                            "Field not found: " + fields[index] + " of " + value + " on value " + result, e);
                }
            }

            return (T) result;
        }

        public T access(Object value, String path) {
            if (value == null) {
                Log.debug("Trying to access null value: " + path);
                return null;
            }

            var fields = path.split("\\.");

            if (fields.length == 0) {
                return (T) value;
            }

            Log.debug("Fields: " + Arrays.toString(fields));

            Object result = value;

            for (int index = 0; index < fields.length; index++) {
                try {
                    Log.debug("Trying to access field: " + fields[index] + " of " + path + " on value " + result);
                    result = result.getClass().getDeclaredField(fields[index]).get(result);
                } catch (IllegalAccessException e) {
                    throw new WorkflowError("Can not access field: " + fields[index] + " of " + path + " on value "
                            + result, e);
                } catch (NoSuchFieldException e) {
                    throw new WorkflowError(
                            "Field not found: " + fields[index] + " of " + path + " on value " + result, e);
                } catch (SecurityException e) {
                    throw new WorkflowError(
                            "Can not access field: " + fields[index] + " of " + path + " on value " + result, e);
                }
            }
            return (T) result;
        }

        public String asString(WorkflowEmitEvent event) {
            var matcher = VARIABLE_PATTERN.matcher(value);

            if (!matcher.find()) {
                return value;
            }

            matcher.reset();

            var result = new StringBuilder();
            int lastEnd = 0;

            for (var match : matcher.results().toList()) {
                String path = match.group(1);

                Log.debug("Resolving variable: " + path);

                result.append(value, lastEnd, match.start());

                var firstDot = path.indexOf('.');

                if (firstDot != -1) {
                    var key = path.substring(0, firstDot);
                    var obj = event.getValues().get(key);

                    if (obj == null) {
                        Log.debug("Variable not found: " + key);
                    }

                    var variable = access(obj, path.substring(firstDot + 1));

                    if (variable == null) {
                        Log.debug("Variable not found: " + path);
                    }

                    result.append(variable);

                } else {
                    var variable = event.getValues().get(path);

                    if (variable == null) {
                        Log.debug("Variable not found: " + path);
                    }

                    result.append(variable);
                }

                lastEnd = match.end();
            }

            result.append(value.substring(lastEnd));
            return result.toString();
        }
    }

    @Data
    public class WorkflowField<C, P> {
        private final String name;

        private FieldProducer<P> producer;
        private FieldConsumer<C> consumer;

        public WorkflowField(String name, Class<C> type) {
            this.name = name;
            fields.add(this);
        }

        public WorkflowField<C, P> produce(FieldProducer producer) {
            this.producer = producer;
            return this;
        }

        public WorkflowField<C, P> consume(FieldConsumer<C> consumer) {
            this.consumer = consumer;
            return this;
        }
    }
}
