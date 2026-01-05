# SmartFarm V2 - Full Project Report

**Project:** SmartFarm V2 (Multi-Agent System)  
**Date:** January 05, 2026  
**Status:** Operational (Phase 1-5 Complete)

---

## 1. System Overview

SmartFarm V2 is a multi-agent simulation where autonomous agents manage a farm's crops, resources, and economy. The backend is built with **JADE (Java Agent Development Framework)**, and the frontend is a real-time web dashboard using **HTML/CSS/JS** connected via **WebSockets**.

### Key Components:
- **Agents:** Autonomous entities (Fields, Drones, Workers, Market Agents) that communicate via ACL messages.
- **Environment:** A virtual farm with weather, day/night cycles, and resource evaporation.
- **Planner (BDI):** An intelligent manager that monitors inventory and auto-orders supplies.
- **GUI:** A web-based interface visualizing every action in real-time.

---

## 2. Agent Workflow

The system uses a **message-driven architecture**. Agents do not call each other's methods directly; they send FIPA-compliant ACL messages.

### A. The Crop Cycle (Core Loop)
1.  **FieldAgent**: Manages a crop (Corn/Wheat). Tracks moisture, health, and growth.
    -   *Trigger:* Moisture < 30% → Sends `REQUEST` to `Irrigator`.
    -   *Trigger:* Disease Symptoms detected → Sends `REQUEST` to `Drone`.
    -   *Trigger:* Growth = 100% → Sends `REQUEST` to `Harvester`.
2.  **DroneAgent**: Patrols and Scans.
    -   Receives scan request → Moves to field → Diagnoses disease (e.g., APHIDS, ROOT_ROT) → Sends `AGREE` to `Sprayer` with diagnosis.
3.  **worker Agents**:
    -   **Irrigator**: Moves to field → Adds water (+50%) → Consumes water from Inventory.
    -   **Sprayer**: Moves to field → Treats disease → Consumes fungicide from Inventory.
    -   **Harvester**: Moves to field → Harvests crops (+10 Inventory) → Field resets (Growth 0%).

### B. The Economy & Market (Phase 4)
1.  **SupplierAgent**: Periodically broadcasts offers (Water @ $5, Fungicide @ $20).
    -   Listens for `BUY` requests → Checks farm funds → Delivers resources.
2.  **ClientAgent (CropBuyer)**: Wants to buy crops.
    -   Polls farm inventory. If crops > 10, it buys them → Sends Money to farm.
3.  **PlannerAgent (Phase 5 - BDI)**: The "Brain".
    -   **Beliefs**: Checks `Inventory` static class.
    -   **Desires**: Keep Water > 50, Fungicide > 5.
    -   **Intentions**: If low, sends `BUY` request to Suppliers using available money.

---

## 3. Frontend Architecture

The GUI is a single-page application (SPA) served by `WebServer.java`.

### File Structure:
-   `src/SmartFarmV2/resources/public/index.html`: Contains all HTML/CSS/JS.
-   **CSS**: Uses Grid/Flexbox for layout. Custom classes for animations (`.moving-drone`, `.night-mode`).
-   **JS**: pure JavaScript (no frameworks). Handles WebSocket connection and DOM updates.

### Key GUI Features:
-   **Farm Grid**: Visual representation of fields and containers.
-   **Agent Cards**: Real-time status of every agent (Location, Battery, State).
-   **Logs**:
    -   **Global Log**: Right sidebar.
    -   **Container Logs**: Mini-logs inside each container (Base, Field, Warehouse) for context-specific events.
-   **Visual Feedback**:
    -   Weather icons (Sunny/Rainy/Storm) affect evaporation rates.
    -   Day/Night cycle (visual overlay + logic).
    -   Inventory counters update instantly.

---

## 4. Backend-Frontend Connection

The bridge between JADE and the Browser is `WebServer.java` (using **Javalin** framework).

### Communication Protocol (WebSockets)

1.  **Initialization**:
    -   Browser opens `ws://localhost:8080/ws`.
    -   Server registers connection.

2.  **Server → Client (Broadcasts):**
    The backend sends JSON messages when state changes.
    *   `FIELD_UPDATE`: `{id: "Field-1", moisture: 45, growth: 10, ...}`
    *   `AGENT_UPDATE`: `{id: "Drone-1", loc: "Field-1", bat: 85}`
    *   `AGENT_MOVE`: `{id: "Drone-1", from: "Base", to: "Field-1"}`
    *   `INVENTORY_UPDATE`: `{water: 100, money: 1500, ...}`
    *   `LOG`: `{message: "[Field-1] Requesting water..."}`

3.  **Handling Updates (JavaScript):**
    -   `socket.onmessage` receives JSON.
    -   `JSON.parse()` decodes data.
    -   `switch(type)` routes to functions like `updateField()`, `updateInventory()`, or `animateAgentMove()`.

---

## 5. How to Run & Extend

### Running the Project
1.  Open terminal in project root.
2.  Run `run_smartfarm_v2.bat`.
3.  Open `http://localhost:8080` in Chrome/Edge.

### Extending the Project
-   **New Agent**: Create class extending `Agent`, register in `Main.java`.
-   **New Crop**: Add to `CropType.java`.
-   **New GUI Element**: Add HTML in `index.html`, add handler in JS `handleMessage()`.

---
