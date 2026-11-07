# Hide native labels in the dialogue NPC preview

Approved: suppress the name and title above the preview model, retaining the UI name below it and title above speech.

Use an entity-specific render scope around the inventory preview call, restored with finally. A client Mixin cancels RenderNPCInterface.renderLivingLabel only for the exact NPC inside that scope. This method contains both native name and title rendering in the installed CustomNPCs JAR. Do not use DataDisplay.setShowName: it also marks updateClient and changes entity configuration.

Test scope isolation, nesting, exception cleanup and thread isolation first; then integrate, compileJava and run relevant regressions. Actual in-game appearance remains a separate restarted-client check.

Completed: render scope, scoped native-label hook, registration and preview integration. compileJava, scope regression and layout regression passed. In-game verification remains pending.
