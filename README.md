Of course! Explaining a complex system in simple terms is the true mark of an expert. Here is a detailed `README.md` file, complete with diagrams, that explains our entire application and its advanced agentic workflow as if to a 5-year-old.

---

# 🤖 My Awesome Magic Chat Box! 🤖

Hello! This is a very special project. It's a Magic Chat Box that you can talk to. It's super smart and can do two amazing things:

1.  **Have a normal chat with you**, like a friend telling you a story one word at a time.
2.  **Build things for you!** If you ask it to "write a program," a special team of little Robot Helpers inside the box wakes up to build it for you.

<br>

## How The Magic Works ✨

Imagine you send a message to the Magic Chat Box. It goes to the "Brain" of the box, which is a very smart robot called the **Team Captain**.

The Team Captain reads your message and decides: "Is this a simple chat, or is this a big project?"

Here is a map of what happens:

```mermaid
graph TD
    A[You Send a Message 💌] --> B{Magic Chat Box};
    B --> C[The Brain 🧠 <br> (Team Captain Robot)];
    C --> D{Is it a simple chat <br> or a big project?};
    D -- "Simple Chat!" --> E[Storyteller Robot 📖];
    E --> F[Tells you the answer <br> one word at a time...];
    D -- "Big Project!" --> G[Team of Coding Robots 👷‍♂️🔧🧐];
    G --> H[Builds your project <br> and shows you the result!];
    F --> I[You See the Answer! 😄];
    H --> I;

    style C fill:#87CEEB,stroke:#333,stroke-width:2px
    style G fill:#90EE90,stroke:#333,stroke-width:2px
    style E fill:#FFD700,stroke:#333,stroke-width:2px
```

<br>

## Meet the Team of Coding Robots! 👷‍♂️🔧🧐

When you ask for a big project (like "write a program"), the **Team Captain** doesn't do the work alone. It calls its special team!

There are three robots on the team:

1.  **The Builder Robot (`CoderAgent`)**: Its job is to build with code, like building with LEGOs. It reads your idea and builds the program.
2.  **The Checker Robot (`TesterAgent`)**: Its job is to check the Builder's work. It builds a little test to make sure the program works and doesn't fall apart.
3.  **The Inspector Robot (`ReviewerAgent`)**: This is the boss! It looks at the program AND the test and decides if it's good enough.

### The Building Game (How the Team Works)

The Team Captain tells the team to start. They play a little game to make sure the project is perfect.

1.  The **Builder Robot** makes the program.
2.  The **Checker Robot** makes the tests.
3.  The **Inspector Robot** looks at everything.
    -   If it's perfect, the Inspector shouts **"APPROVED! 🎉"** and the team shows you the finished project.
    -   If it's not right, the Inspector shouts **"REJECTED! 😠"** and tells the Builder Robot what was wrong.
4.  If it was rejected, the team **tries again**! The Builder Robot uses the Inspector's notes to make it better. They can try up to 3 times.

Here is a map of their game:

```mermaid
graph TD
    subgraph The Coding Team's Game
        A[Team Captain gets the project] --> B[1. Builder Robot <br> (Makes the code)];
        B --> C[2. Checker Robot <br> (Makes the tests)];
        C --> D{3. Inspector Robot <br> (Is it good?)};
        D -- "✅ YES! APPROVED!" --> E[✨ All Done! Show the User!];
        D -- "❌ NO! REJECTED!" --> F[Inspector gives notes to Builder];
        F --> B;
    end

    style A fill:#87CEEB,stroke:#333,stroke-width:2px
```

<br>

## For the Grown-Ups (What the Code Does) 🤓

This project is a modern, reactive web application built with **Spring Boot 3** and **Spring AI**. It demonstrates a sophisticated, resilient, multi-agent workflow.

*   **The Brain (Team Captain)** is the `OrchestratorService`. It uses an LLM call to classify user intent and routes traffic to the appropriate service.
*   **The Storyteller Robot** is the `ChatService`. It provides a true, word-by-word streaming experience for conversational chat using Project Reactor's `Flux` and a "hot stream" (`share()`) pattern.
*   **The Team of Coding Robots** is the `CodeAssistantService`. It manages the agentic workflow, orchestrating the other agents in a non-blocking, reactive loop using `Mono.expand`.
    *   **Builder Robot**: `CoderAgent` - Responsible for generating code. Includes programmatic safeguards to clean LLM output.
    *   **Checker Robot**: `TesterAgent` - Responsible for generating tests. Also includes safeguards.
    *   **Inspector Robot**: `ReviewerAgent` - Uses a simple, text-based contract (`"APPROVED" / "REJECTED"`) for resilient, non-JSON communication, making the workflow robust against LLM failures.

<br>

## How to Run the App 🚀

You need two windows in your computer's terminal.

1.  **Start the Brain (Backend):**
    ```bash
    # Go to the project folder
    cd chat-app

    # Tell Maven to run the app
    ./mvnw spring-boot:run
    ```

2.  **Start the Face (Frontend):**
    ```bash
    # Go to the ui folder inside the project
    cd ui

    # Install all the needed toys
    npm install

    # Start the app
    npm run dev
    ```

Now open a web browser and go to `http://localhost:5173` to play with the Magic Chat Box!

<br>

## Coolest Features ⭐

*   **True Word-by-Word Streaming:** The chat feels alive because answers appear one piece at a time, not all at once.
*   **Super Smart Router:** The app knows when you're just chatting versus when you need a coding project built.
*   **A Real Robot Team:** The agents work together, review each other's work, and even try again when they make mistakes!
*   **Super Resilient:** The app is built to not crash, even if the AI robots get confused and don't send back perfect messages. It knows how to handle mistakes gracefully.