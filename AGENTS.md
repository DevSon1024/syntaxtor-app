# AI Agent Instructions for Syntaxtor Development

This document serves as the absolute source of truth for any AI agent or LLM assisting with the development of the **Syntaxtor** project. You must strictly adhere to these rules, architectural guidelines, and development philosophies before generating or modifying any code.

## 1. Core Development Philosophy

- **Goal:** Syntaxtor is a high-performance, native Android syntax-highlighting text editor built with Kotlin, Jetpack Compose, Coroutines/Flow, Clean Architecture, and an efficient custom Editor Engine.
- **Flawless Execution:** The app MUST handle large text files natively without bottleneck bugs. Performance regressions, UI lag, and keystroke stuttering (especially during real-time syntax parsing and line-number rendering) are absolutely unacceptable.
- **Zero Crash Tolerance:** Improve code robustness to ensure the app does not crash under any circumstances. Always prioritize graceful degradation (e.g., showing a read-only mode or safe error states) over throwing unhandled exceptions when parsing unknown files.
- **No Hallucinations:** Only use existing APIs, classes, and resources within the project. Do not invent external diffing libraries unless explicitly approved. If you are unsure about an existing implementation, ask the developer to fetch the file contents.
- **One Line Explanation:** Do not explain things in long phrases and paragraphs. Be short and precise. Do not explain things if it is not required.

## 2. UI / Jetpack Compose Guidelines

- **Framework:** Jetpack Compose is the exclusive UI framework. Avoid legacy XML layouts entirely for UI screens.
- **Design Principle:** Always enforce a **mobile-first approach**. Layouts, touch targets, text editor padding, and bottom sheets must be designed considering mobile screen sizes and orientations first.
- **Material Design:** Strictly utilize Material Design 3 (M3) components (`androidx.compose.material3.*`) to ensure a clean, modern, IDE-like experience.
- **Composables & Recomposition:** The text editor is highly sensitive to recomposition. Keep composable functions highly focused. Prevent unnecessary recompositions on every keystroke by isolating `BasicTextField` state and utilizing `remember` and targeted StateFlow updates carefully.
- **State Hoisting:** Prefer state hoisting for UI components to keep them stateless. **Never** perform heavy regex syntax calculations or file saves directly within composable functions.

## 3. Code Quality & Performance Optimization

- **Language:** Kotlin is the exclusive programming language.
- **Asynchronous Operations:** Use Kotlin Coroutines and Flows (`StateFlow`/`SharedFlow`) for all background tasks.
- **Null Safety & Crash Prevention:** Handle nullable types safely and exhaustively. **Never** use the not-null assertion operator (`!!`). Catch specific exceptions rather than generic `Exception` where possible.
- **Eliminate Editor Bottlenecks:**
  - **Syntax Highlighting:** Ensure regex-based syntax highlighting is optimized and debounced where necessary. Do not block the main UI thread when formatting `AnnotatedString` blocks for massive files.
  - **Disk I/O & History:** Always dispatch `SafFileManager` tasks, Auto-Saves, and Room Database operations (like chunk-based version history) to `Dispatchers.IO`.
  - **Memory Management:** Be aggressive about optimizing memory. Avoid duplicating massive string allocations when diffing files or maintaining save states.

## 4. Documentation & Update Tracking

You must actively maintain the project's changelog. After every completed task, error resolution, or feature addition, you must append an entry to the `update_details.md` file.

**Format and Rules for `update_details.md`:**

- Do NOT read or rewrite the whole file every time. Simply append the new data at the very end of the document.
- Include a Date and Time stamp for the update.
  Whenever a fix, optimization, or feature is completed, you MUST document it using the following format:

- **Issue:** (Briefly describe the exact issue or bottleneck that was just solved)
- **Type:** (Specify the category: e.g., Error, Bug, UI, Performance, Architecture, EditorEngine, Feature)
- **Solution:** (Explain how the issue was solved. Maximum 10 lines.)
- After the details of the latest update, you must append exactly `---` on a new line to close out that specific session.
- Do not include any conversational filler in the file.

## 5. Version Control (Git) Protocol

- **Do not commit or push** any changes to the repository until explicitly being asked to do so by the developer.
