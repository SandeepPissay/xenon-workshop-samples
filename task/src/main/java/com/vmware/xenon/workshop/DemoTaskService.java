package com.vmware.xenon.workshop;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

public class DemoTaskService extends TaskService<DemoTaskService.DemoTaskState> {

    public static final String FACTORY_LINK = ServiceUriPaths.CORE + "/demo-tasks";

    public static FactoryService createFactory() {
        return TaskFactoryService.create(DemoTaskService.class);
    }

    public enum SubStage {
        MOVE_TO_FINISHED
    }

    public static class DemoTaskState extends TaskService.TaskServiceState {

        /**
         * Sub-stage of the current task.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage subStage;

        // Additional task state goes here
    }

    public DemoTaskService() {
        super(DemoTaskState.class);
    }

    /**
     * This method is invoked when a factory POST causes a new task to be created.
     * @param startOp Supplies the POST {@link Operation} which has caused this task to be created.
     */
    @Override
    public void handleStart(Operation startOp) {
        DemoTaskState task = validateStartPost(startOp);
        if (task == null) {
            return;
        }

        initializeState(task, startOp);

        startOp.setBody(task)
                .setStatusCode(Operation.STATUS_CODE_ACCEPTED)
                .complete();

        sendSelfPatch(task, TaskState.TaskStage.STARTED, this::initializePatchState);
    }

    /**
     * This method validates the initial state of a task.
     * @param startOp Supplies the POST {@link Operation} which has caused this task to be created.
     * @return A {@link DemoTaskState} initialized from the operation, or null on failure.
     */
    @Override
    protected DemoTaskState validateStartPost(Operation startOp) {
        DemoTaskState taskState = super.validateStartPost(startOp);
        if (taskState == null) {
            return null;
        }

        if (!ServiceHost.isServiceCreate(startOp)) {
            return taskState;
        }

        if (taskState.taskInfo != null && taskState.taskInfo.stage != null) {
            startOp.fail(new IllegalArgumentException("taskInfo.stage may not be specified"));
            return null;
        }

        if (taskState.subStage != null) {
            startOp.fail(new IllegalArgumentException("subStage may not be specified"));
            return null;
        }

        // Additional input validation should go here.

        return taskState;
    }

    /**
     * This method initializes a {@link DemoTaskState} from the body of a valid POST operation.
     * @param taskState Supplies the task state to be initialized.
     * @param startOp Supplies the POST {@link Operation} which has caused this task to be created.
     */
    @Override
    protected void initializeState(DemoTaskState taskState, Operation startOp) {
        super.initializeState(taskState, startOp);
        taskState.taskInfo.stage = TaskState.TaskStage.CREATED;
    }

    private void initializePatchState(DemoTaskState taskState) {
        taskState.taskInfo.stage = TaskState.TaskStage.STARTED;
        taskState.subStage = SubStage.MOVE_TO_FINISHED;
    }

    @Override
    public void handlePatch(Operation patchOp) {
        DemoTaskState currentState = getState(patchOp);
        DemoTaskState patchBody = patchOp.getBody(DemoTaskState.class);

        if (!validateTransition(patchOp, currentState, patchBody)) {
            return;
        }

        super.updateState(currentState, patchBody);
        patchOp.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            // Should never happen; validateTransition will not allow a transition to CREATED.
            break;
        case STARTED:
            processSubStage(currentState);
            break;
        case FINISHED:
            logInfo("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", currentState.failureMessage);
            break;
        case CANCELLED:
            logInfo("Task cancelled: not implemented, ignoring");
            break;
        }
    }

    @Override
    public boolean validateTransition(Operation patchOp, DemoTaskState currentState,
            DemoTaskState patchBody) {
        if (!super.validateTransition(patchOp, currentState, patchBody)) {
            return false;
        }

        if (patchBody.taskInfo.stage == TaskState.TaskStage.STARTED) {
            // A sub-stage must be specified when in STARTED stage
            if (patchBody.subStage == null) {
                patchOp.fail(new IllegalArgumentException("subStage is required"));
                return false;
            }

            // A patch cannot transition the task to an earlier sub-stage
            if (currentState.taskInfo.stage == TaskState.TaskStage.STARTED
                    && patchBody.subStage.ordinal() < currentState.subStage.ordinal()) {
                patchOp.fail(new IllegalArgumentException("Invalid sub-stage"));
                return false;
            }
        }

        return true;
    }

    private void processSubStage(DemoTaskState currentState) {
        switch (currentState.subStage) {
        case MOVE_TO_FINISHED:
            logInfo("Moving to FINISHED state");
            sendSelfPatch(currentState, TaskState.TaskStage.FINISHED, null);
            break;
        }
    }
}
