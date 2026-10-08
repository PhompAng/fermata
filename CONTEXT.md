# Fermata

A Kotlin/Android library (named after the musical hold sign: hold until released, then continue) that lets an in-flight operation pause, hand a typed question to the UI, and resume with the answer, so MVVM + Clean Architecture code reads top to bottom instead of being split across event emitters and callback methods.

## Language

**Operation**:
A unit of work that may need to pause for user input before it can finish. Lives in a ViewModel (primary) or, when the pause is part of one domain transaction, inside a use case.
_Avoid_: Flow, action, job

**Interaction**:
A typed question an Operation hands to the UI. Each Interaction declares the type of Answer it expects. Defined by the app, not the library.
_Avoid_: Event, dialog, request, confirmation

**Answer**:
The typed value the UI returns for a specific Interaction, which lets the Operation resume.
_Avoid_: Result, response, callback

**InteractionPort**:
The boundary an Operation asks through. Exactly one per ViewModel; a use case receives it explicitly from its caller rather than discovering it.
_Avoid_: Interactor (means use case in Clean Architecture), prompter, dialog manager

**Dismissal**:
The user leaving an Interaction without giving an Answer (back press, tap outside). Not an Answer: it aborts the asking Operation.
_Avoid_: Cancel (overloaded with coroutine cancellation and with "No" answers), close

## Flagged ambiguities

- "Confirmation" is often used in app code for what this library calls an **Interaction**. Confirmation is one kind of Interaction whose Answer is a yes/no; it is not the general concept.

## Example dialogue

**Dev:** When the cashier removes the last item carrying a promo code, we need to warn them before releasing the code.
**Expert:** So the remove Operation hands the UI an Interaction, "this will release PROMO-A, continue?", and pauses.
**Dev:** And the dialog's button press is the Answer, a Boolean here.
**Expert:** Right. The Operation resumes with that Answer and either releases the code or skips it. Nothing about "where was I" lives in the dialog.
**Dev:** What if they tap outside the dialog?
**Expert:** That's a Dismissal, not an Answer. The Operation aborts as if they never started removing the item.
**Dev:** And the remove use case asks directly, not the ViewModel?
**Expert:** Only because releasing the code is part of the same transaction. The ViewModel hands it the InteractionPort; the use case never goes looking for one.
