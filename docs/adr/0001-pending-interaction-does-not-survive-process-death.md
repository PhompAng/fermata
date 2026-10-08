# A pending Interaction does not survive process death

An Interaction is a suspension point inside a running coroutine. Configuration changes are covered because the ViewModel and its pending Interaction state survive and the UI re-renders the dialog. Process death kills the coroutine, and no amount of persisting the Interaction can resume the stack frame that asked it. We deliberately do not persist the pending Interaction through `SavedStateHandle`: restoring the dialog without the Operation behind it would produce an Answer that goes nowhere, which is worse than showing nothing. The library guarantees exactly what `viewModelScope` guarantees. A flow that must survive process death is a persisted domain state machine and belongs in the app.

## Considered options

- Persist the pending Interaction via `SavedStateHandle` and re-show it. Rejected: the Answer has no consumer after restoration.
- Make Operations replayable from a saved step. Rejected: that is a workflow engine, out of scope for a confirmation library.
