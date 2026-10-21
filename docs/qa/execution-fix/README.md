# Execution and message-layout repair — 2026-10-21

The production failure's checkpoint error channel contained `RuntimeError('已达到工具调用上限，请缩小任务范围后重试')`. The model and JD tools had already succeeded. A hard 20-call budget aborted parallel tool execution; the old error handler replaced the cause with a generic message. This was not evidence of an invalid model ID.

Changes:
- Default tool budget is 48. Query tools reserve two calls for draft validation. Exhaustion produces a handled tool result and a budget-warning event so the model can finish with existing evidence. Time/step limits remain enforced; provider auth/model/rate-limit/connection errors get distinct safe messages.
- Model-turn events separate tool-preface summaries from final answers. User text is left-aligned, plans and work summaries are folded by default, and actual completed/started tool counts drive the progress bar. Retry progress counts only the current attempt. Tool timeline plans/summaries are also folded.
- User's final model choice, `moonshotai/kimi-k3-free`, is stored in production settings and private Agent env. The provider `/models` listing and a real tool-call response confirmed it is available. No paid fallback is configured. Free-tier reliability is not guaranteed by the provider.

Validation:
- Python: 26 tests passed, including tool-budget exhaustion returning to the model, separate work/final messages, and existing permission/RAG/crawler checks. Vue TypeScript production build passed.
- The user's existing failed task was resumed under Kimi Free, preserving its checkpoint. It completed in 148.4 seconds after resume, with 24 total tool calls across attempts, 8 selected parts, no compatibility validation errors, and a 1,196-character final answer. No duplicate conversation was created; user configuration was not automatically saved/published.
- Four production containers healthy after deployment/restart. SQLite and checkpoint backups are private under `/www/smartdiy-docker/backups/execution-fix-20261021-134238`.
- Browser verified left-aligned user text; collapsed details open/close; desktop and 390×844 mobile layout; no mobile horizontal overflow. `desktop-folded.jpg` and `mobile-folded.jpg` show real prior test-conversation replay in the isolated local environment using the deployed UI source, not the user's private production conversation.
