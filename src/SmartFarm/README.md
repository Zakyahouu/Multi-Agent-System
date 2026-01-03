# 🌾 Smart Farm Multi-Agent Simulation

A complete multi-agent system simulation using **JADE Framework** with a **Javalin-based Web GUI** for real-time monitoring.

## 📋 Overview

This simulation demonstrates a smart farming scenario with the following agents:

| Agent | Type | Container | Description |
|-------|------|-----------|-------------|
| **SoilSensorAgent** | Reactive | Field-Container | Monitors soil moisture every 5 seconds |
| **FarmControllerAgent** | Deliberative | Main-Container | Orchestrates farm operations and negotiations |
| **InspectorDroneAgent** | Mobile | Main-Container | Physically migrates between containers for inspections |
| **SupplierAgent** (x2) | Cognitive | Main-Container | Competes in Contract Net Protocol for water supply |

## 🔄 Workflow

```
┌───────────────────────────────────────────────────────────────────────┐
│                        SMART FARM WORKFLOW                            │
├───────────────────────────────────────────────────────────────────────┤
│                                                                       │
│   1. SENSING          2. DECISION          3. INSPECTION              │
│   ┌─────────┐        ┌─────────┐          ┌─────────┐                │
│   │ Sensor  │──────▶ │Controller│────────▶ │  Drone  │                │
│   │ <30%    │ REQUEST│ Weather? │ INSPECT  │ doMove()│                │
│   └─────────┘        └─────────┘          └─────────┘                │
│        │                  │                    │                      │
│        │                  │ ☀️ No Rain         │                      │
│        │                  ▼                    ▼                      │
│        │            4. NEGOTIATION      Field-Container               │
│        │            ┌─────────┐              │                        │
│        │            │   CNP   │◀─────────────┘                        │
│        │            │ CFP →   │              CONFIRM                  │
│        │            └────┬────┘                                       │
│        │                 │                                            │
│        │      ┌──────────┼──────────┐                                │
│        │      ▼          ▼          ▼                                │
│        │  ┌────────┐ ┌────────┐                                      │
│        │  │Supplier│ │Supplier│                                      │
│        │  │   1    │ │   2    │                                      │
│        │  │ $32.50 │ │ $28.00 │ ◀── WINNER                           │
│        │  └────────┘ └────────┘                                      │
│        │                                                              │
└───────────────────────────────────────────────────────────────────────┘
```

## 🚀 How to Run

### Option 1: Using Eclipse

1. **Refresh the project** in Eclipse (F5 or right-click → Refresh)
2. Open `src/SmartFarm/com/farm/Main.java`
3. Right-click → Run As → Java Application
4. Open browser at `http://localhost:8080`

### Option 2: Using the Batch Script

1. Double-click `run_smart_farm.bat`
2. Open browser at `http://localhost:8080`

### Option 3: Command Line

```batch
cd c:\Users\ZAKAR\eclipse-workspace\Multi_Agent_Project

java -cp "bin;src/SmartFarm/resources;C:/Users/ZAKAR/Bureau/Univ/IA Master/S3/iadsma (mas)/Tp/jade/lib/jade.jar;lib/javalin/*" com.farm.Main
```

## 📁 Project Structure

```
Multi_Agent_Project/
├── src/
│   ├── SmartFarm/
│   │   ├── com/farm/
│   │   │   ├── agents/
│   │   │   │   ├── SoilSensorAgent.java      # Reactive sensor
│   │   │   │   ├── FarmControllerAgent.java  # Deliberative controller
│   │   │   │   ├── InspectorDroneAgent.java  # Mobile drone
│   │   │   │   └── SupplierAgent.java        # Competitive supplier
│   │   │   ├── gui/
│   │   │   │   └── WebServer.java            # Javalin + WebSocket
│   │   │   └── Main.java                     # Application launcher
│   │   └── resources/
│   │       └── public/
│   │           └── index.html                # Dashboard UI
│   ├── Agents/                               # (Existing code)
│   └── ...
├── lib/
│   └── javalin/                              # Javalin dependencies (16 JARs)
└── run_smart_farm.bat                        # Quick launch script
```

## 🖥️ Dashboard Features

- **Real-time moisture gauge** with color-coded thresholds
- **Animated drone** that moves between containers visually
- **Weather forecast display** (sun/rain)
- **Supplier market view** with live price updates
- **Event log** with color-coded messages by agent type
- **Session statistics** (alerts, inspections, purchases)

## 🔧 Technical Details

### Agent Communication
- **ACL Messages**: REQUEST, CONFIRM, CFP, PROPOSE, ACCEPT_PROPOSAL, REJECT_PROPOSAL
- **Contract Net Protocol**: Used for water supplier negotiation
- **Directory Facilitator (DF)**: Service registration and discovery

### Agent Mobility
- `InspectorDroneAgent` implements `Serializable`
- Uses `doMove()` with `ContainerID` for migration
- `beforeMove()` and `afterMove()` callbacks for state management

### Web Technologies
- **Javalin 5.6.3** for HTTP server
- **WebSocket** at `/stream` endpoint
- **Bootstrap 5** for responsive dashboard
- **JSON messages** for real-time updates

## 📊 Expected Console Output

```
╔══════════════════════════════════════════════════════════════╗
║        🌾 SMART FARM MULTI-AGENT SIMULATION 🌾               ║
╚══════════════════════════════════════════════════════════════╝

[Main] Step 1: Starting Web Server...
╔══════════════════════════════════════════════════════════════╗
║           🌾 SMART FARM WEB SERVER STARTED 🌾                ║
║   Dashboard: http://localhost:8080                           ║
╚══════════════════════════════════════════════════════════════╝

[Main] Step 2: Initializing JADE Runtime...
[Main] Step 3: Creating Main-Container...
[Main] ✅ Main-Container created successfully.
[Main] Step 4: Creating Field-Container...
[Main] ✅ Field-Container created successfully.
[Main] Step 5: Starting Agents...
...
╔══════════════════════════════════════════════════════════════╗
║          🚀 ALL SYSTEMS OPERATIONAL 🚀                       ║
╚══════════════════════════════════════════════════════════════╝
```

## 🛠️ Troubleshooting

| Issue | Solution |
|-------|----------|
| Port 8080 in use | Stop other web servers or change port in `WebServer.java` |
| JADE classes not found | Verify JADE JAR path in `.classpath` |
| WebSocket not connecting | Check browser console for errors |
| Drone not moving | Verify `Field-Container` was created successfully |

---
*Created for IADSMA (MAS) Course - Smart Farm Simulation*
