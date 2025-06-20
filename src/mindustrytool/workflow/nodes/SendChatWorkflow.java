package mindustrytool.workflow.nodes;

import mindustry.gen.Call;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class SendChatWorkflow extends WorkflowNode {
    private WorkflowField<String, Object> messageField = new WorkflowField<>("message", String.class)
            .consume(new FieldConsumer<>(String.class)
                    .defaultValue("Hello"));

    public SendChatWorkflow() {
        super("SendChat", WorkflowGroup.ACTION, WorkflowColor.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        String message = messageField.getConsumer().asString(event);

        Call.sendMessage(message);
    }
}
