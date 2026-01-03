/**
 * Smart Farm V2 - Frontend Application
 * 
 * IMPORTANT: This frontend is purely a DISPLAY layer.
 * ALL state comes from the backend agents via WebSocket.
 * NO simulation logic runs here - agents control everything.
 */

// ============================================================
//                    STATE (from backend only)
// ============================================================

const state = {
    connected: false,
    fields: {},      // Populated by FIELD_UPDATE from backend
    drones: {},      // Populated by DRONE_* events from backend
    harvesters: {},  // Populated by HARVEST_* events from backend
    suppliers: {},   // Populated by SUPPLIER_* events from backend
    bdi: {
        beliefs: [],
        desires: [],
        intentions: []
    },
    economy: {
        income: 0,
        expenses: 0
    },
    prediction: {
        value: null,
        confidence: 0,
        samples: 0
    }
};

// ============================================================
//                    WEBSOCKET CONNECTION
// ============================================================

let ws = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 10;

function connect() {
    const wsUrl = `ws://${window.location.hostname}:8080/stream`;
    console.log('[WebSocket] Connecting to:', wsUrl);

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        console.log('[WebSocket] Connected!');
        state.connected = true;
        reconnectAttempts = 0;
        updateConnectionStatus(true);
        addLog('marketLog', 'system', 'Connected to Smart Farm backend');
    };

    ws.onclose = () => {
        console.log('[WebSocket] Disconnected');
        state.connected = false;
        updateConnectionStatus(false);

        // Attempt reconnection
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            const delay = Math.min(1000 * reconnectAttempts, 5000);
            console.log(`[WebSocket] Reconnecting in ${delay}ms... (attempt ${reconnectAttempts})`);
            setTimeout(connect, delay);
        }
    };

    ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error);
    };

    ws.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            handleBackendMessage(message);
        } catch (e) {
            console.error('[WebSocket] Parse error:', e);
        }
    };
}

function updateConnectionStatus(connected) {
    const statusEl = document.getElementById('connectionStatus');
    if (connected) {
        statusEl.classList.add('connected');
        statusEl.classList.remove('disconnected');
        statusEl.querySelector('.status-text').textContent = 'Connected';
    } else {
        statusEl.classList.remove('connected');
        statusEl.classList.add('disconnected');
        statusEl.querySelector('.status-text').textContent = 'Disconnected';
    }
}

// ============================================================
//                    MESSAGE HANDLERS
// ============================================================

function handleBackendMessage(message) {
    const { type, data, timestamp } = message;

    switch (type) {
        // Connection
        case 'CONNECTION':
            console.log('[Backend] Welcome:', data.message);
            break;

        // Field updates from agents
        case 'FIELD_UPDATE':
            handleFieldUpdate(data);
            break;

        // Moisture readings from sensors
        case 'MOISTURE_READING':
            handleMoistureReading(data);
            break;

        // Drone events
        case 'DRONE_DISPATCH':
        case 'DRONE_MOVING':
        case 'DRONE_ARRIVED':
        case 'DRONE_INSPECTING':
        case 'DRONE_INSPECTION_DONE':
        case 'DRONE_RETURNING':
        case 'DRONE_CONFIRMATION_SENT':
            handleDroneEvent(type, data, timestamp);
            break;

        case 'DRONE_MOVE':
            handleDroneMove(data);
            break;

        // Harvester events
        case 'HARVEST_COMPLETE':
            handleHarvestComplete(data, timestamp);
            break;

        // Supplier/Market events
        case 'SUPPLIER_PROPOSAL':
        case 'SUPPLIER_WON':
        case 'SUPPLIER_LOST':
            handleSupplierEvent(type, data, timestamp);
            break;

        case 'SUPPLIER_UPDATE':
            handleSupplierUpdate(data);
            break;

        // CNP Auction events
        case 'CNP_START':
        case 'CNP_PROPOSAL':
        case 'CNP_ACCEPT':
        case 'CNP_REJECT':
            handleCNPEvent(type, data, timestamp);
            break;

        // BDI updates from FarmerBDI agent
        case 'BDI_UPDATE':
            handleBDIUpdate(data);
            break;

        // Economy updates
        case 'ECONOMY_UPDATE':
            handleEconomyUpdate(data);
            break;

        // AI Prediction updates
        case 'PREDICTION_UPDATE':
            handlePredictionUpdate(data);
            break;

        // Weather
        case 'WEATHER_UPDATE':
            handleWeatherUpdate(data, timestamp);
            break;

        // Agent lifecycle
        case 'AGENT_START':
            handleAgentStart(data, timestamp);
            break;

        case 'AGENT_STOP':
            handleAgentStop(data, timestamp);
            break;

        // Agent interactions (for logging)
        case 'AGENT_INTERACTION':
            handleAgentInteraction(data, timestamp);
            break;

        // Sensor alerts
        case 'SENSOR_ALERT':
            handleSensorAlert(data, timestamp);
            break;

        default:
            console.log('[Backend] Unknown event:', type, data);
    }
}

// ============================================================
//                    FIELD HANDLERS
// ============================================================

function handleFieldUpdate(data) {
    const { id, crop, moisture, growth, stage, sprinklerOn } = data;

    // Store in state
    state.fields[id] = { crop, moisture, growth, stage, sprinklerOn };

    // Update UI
    renderFields();
}

function handleMoistureReading(data) {
    const { fieldId, moisture, timestamp } = data;

    if (state.fields[fieldId]) {
        state.fields[fieldId].moisture = moisture;
        renderFields();
    }
}

function renderFields() {
    const container = document.getElementById('fieldsContainer');
    const fieldIds = Object.keys(state.fields).sort((a, b) => a - b);

    if (fieldIds.length === 0) {
        container.innerHTML = '<div class="field-placeholder"><span>Waiting for field data from agents...</span></div>';
        return;
    }

    container.innerHTML = fieldIds.map(id => {
        const field = state.fields[id];
        const moistureClass = field.moisture < 30 ? 'critical' : field.moisture < 50 ? 'warning' : 'good';
        const growthClass = field.growth >= 100 ? 'ready' : 'growing';

        return `
            <div class="field-card ${field.stage || ''}">
                <div class="field-header">
                    <span class="field-id">Field ${id}</span>
                    <span class="field-crop">${field.crop || 'Unknown'}</span>
                </div>
                <div class="field-stats">
                    <div class="stat moisture ${moistureClass}">
                        <span class="stat-icon">💧</span>
                        <span class="stat-value">${field.moisture}%</span>
                        <span class="stat-label">Water</span>
                    </div>
                    <div class="stat growth ${growthClass}">
                        <span class="stat-icon">🌱</span>
                        <span class="stat-value">${field.growth}%</span>
                        <span class="stat-label">Growth</span>
                    </div>
                </div>
                ${field.sprinklerOn ? '<div class="sprinkler-on">💦 Irrigating</div>' : ''}
                ${field.stage === 'harvesting' ? '<div class="harvesting">🚜 Harvesting</div>' : ''}
                ${field.stage === 'inspecting' ? '<div class="inspecting">🔍 Inspecting</div>' : ''}
            </div>
        `;
    }).join('');
}

// ============================================================
//                    DRONE HANDLERS
// ============================================================

function handleDroneEvent(type, data, timestamp) {
    const droneId = data.droneId || data.drone;

    // Update drone state
    if (!state.drones[droneId]) {
        state.drones[droneId] = { state: 'idle', target: null };
    }

    switch (type) {
        case 'DRONE_DISPATCH':
            state.drones[droneId].state = 'dispatched';
            state.drones[droneId].target = data.fieldId || data.target;
            addLog('droneLog', 'dispatch', `${droneId} → Field ${data.fieldId || data.target}`, timestamp);
            break;

        case 'DRONE_MOVING':
            state.drones[droneId].state = 'flying';
            addLog('droneLog', 'flying', `${droneId} flying to Field ${data.to || data.target}`, timestamp);
            break;

        case 'DRONE_ARRIVED':
            state.drones[droneId].state = 'arrived';
            addLog('droneLog', 'arrive', `${droneId} arrived at Field ${data.fieldId}`, timestamp);
            break;

        case 'DRONE_INSPECTING':
            state.drones[droneId].state = 'scanning';
            addLog('droneLog', 'scan', `${droneId} scanning Field ${data.fieldId}...`, timestamp);
            break;

        case 'DRONE_INSPECTION_DONE':
            state.drones[droneId].state = 'done';
            addLog('droneLog', 'complete', `${droneId} ✓ inspection complete`, timestamp);
            break;

        case 'DRONE_RETURNING':
            state.drones[droneId].state = 'returning';
            state.drones[droneId].target = null;
            addLog('droneLog', 'return', `${droneId} returning to base`, timestamp);
            break;

        case 'DRONE_CONFIRMATION_SENT':
            state.drones[droneId].state = 'idle';
            break;
    }

    renderDrones();
}

function handleDroneMove(data) {
    const { droneId, from, to, state: droneState } = data;

    // This is for harvester movement too
    if (droneId && droneId.includes('Harvester')) {
        if (!state.harvesters[droneId]) {
            state.harvesters[droneId] = { state: 'idle', target: null };
        }
        state.harvesters[droneId].state = droneState;
        state.harvesters[droneId].target = to;

        if (droneState === 'driving') {
            addLog('harvesterLog', 'driving', `${droneId} → ${to}`, new Date().toISOString());
        }

        renderHarvesters();
    }
}

function renderDrones() {
    const container = document.getElementById('dronesContainer');
    const droneIds = Object.keys(state.drones).sort();

    if (droneIds.length === 0) {
        container.innerHTML = '<div class="agent-placeholder">Waiting for drone agents...</div>';
        return;
    }

    container.innerHTML = droneIds.map(id => {
        const drone = state.drones[id];
        const stateClass = drone.state || 'idle';

        return `
            <div class="agent-card drone ${stateClass}">
                <span class="agent-icon">🛸</span>
                <span class="agent-name">${id}</span>
                <span class="agent-state">${drone.state || 'idle'}</span>
                ${drone.target ? `<span class="agent-target">→ F${drone.target}</span>` : ''}
            </div>
        `;
    }).join('');
}

// ============================================================
//                    HARVESTER HANDLERS
// ============================================================

function handleHarvestComplete(data, timestamp) {
    const { fieldId, crop, amount } = data;
    addLog('harvesterLog', 'harvest', `✓ Harvested ${crop || 'crops'} from Field ${fieldId}`, timestamp);
    addLog('marketLog', 'auction', `🏪 Starting auction for ${crop || 'crops'} from Field ${fieldId}`, timestamp);
}

function renderHarvesters() {
    const container = document.getElementById('harvestersContainer');
    const harvesterIds = Object.keys(state.harvesters).sort();

    if (harvesterIds.length === 0) {
        container.innerHTML = '<div class="agent-placeholder">Waiting for harvester agents...</div>';
        return;
    }

    container.innerHTML = harvesterIds.map(id => {
        const harvester = state.harvesters[id];
        const stateClass = harvester.state || 'idle';

        return `
            <div class="agent-card harvester ${stateClass}">
                <span class="agent-icon">🚜</span>
                <span class="agent-name">${id}</span>
                <span class="agent-state">${harvester.state || 'idle'}</span>
                ${harvester.target ? `<span class="agent-target">→ ${harvester.target}</span>` : ''}
            </div>
        `;
    }).join('');
}

// ============================================================
//                    SUPPLIER/MARKET HANDLERS
// ============================================================

function handleSupplierEvent(type, data, timestamp) {
    const { supplier, supplierId, bid, price, item } = data;
    const name = supplier || supplierId;

    if (!state.suppliers[name]) {
        state.suppliers[name] = { wins: 0, totalSpent: 0 };
    }

    switch (type) {
        case 'SUPPLIER_PROPOSAL':
            addLog('marketLog', 'bid', `${name} bids $${bid || price}`, timestamp);
            break;

        case 'SUPPLIER_WON':
            state.suppliers[name].wins++;
            state.suppliers[name].totalSpent += (price || bid || 0);
            addLog('marketLog', 'win', `🏆 ${name} WINS! Pays $${price || bid}`, timestamp);
            break;

        case 'SUPPLIER_LOST':
            addLog('marketLog', 'lose', `${name} lost auction`, timestamp);
            break;
    }

    renderSuppliers();
}

function handleSupplierUpdate(data) {
    // Direct supplier state update from backend
    if (Array.isArray(data)) {
        data.forEach(s => {
            state.suppliers[s.name || s.id] = {
                budget: s.budget,
                spent: s.spent || s.totalSpent,
                wins: s.wins
            };
        });
    }
    renderSuppliers();
}

function handleCNPEvent(type, data, timestamp) {
    switch (type) {
        case 'CNP_START':
            addLog('marketLog', 'cnp', `📢 Water Auction Started`, timestamp);
            break;

        case 'CNP_PROPOSAL':
            addLog('marketLog', 'bid', `  ${data.supplier}: $${data.price.toFixed(2)}`, timestamp);
            break;

        case 'CNP_ACCEPT':
            // Second-price auction results
            if (data.bids && data.bids.length > 0) {
                addLog('marketLog', 'auction', `🏪 Auction Complete - ${data.bids.length} bids received:`, timestamp);

                // Show all bids sorted by price
                const sortedBids = [...data.bids].sort((a, b) => b.bid - a.bid);
                sortedBids.forEach((bid, index) => {
                    const isWinner = bid.supplier === data.winner;
                    if (isWinner) {
                        addLog('marketLog', 'win',
                            `  🏆 ${bid.supplier}: $${bid.bid.toFixed(2)} (WINNER - pays $${data.payment.toFixed(2)})`,
                            timestamp);
                    } else {
                        addLog('marketLog', 'bid',
                            `    ${bid.supplier}: $${bid.bid.toFixed(2)}`,
                            timestamp);
                    }
                });

                // Highlight payment
                if (data.winningBid !== data.payment) {
                    addLog('marketLog', 'accept',
                        `💰 Second-Price Auction: Winner bid $${data.winningBid.toFixed(2)}, pays $${data.payment.toFixed(2)}`,
                        timestamp);
                }
            } else {
                // Fallback for simple format
                addLog('marketLog', 'accept', `✓ ${data.supplier} @ $${data.price || data.payment}`, timestamp);
            }
            break;

        case 'CNP_REJECT':
            // Already handled in detailed auction results above
            break;
    }
}

function renderSuppliers() {
    const container = document.getElementById('suppliersContainer');
    const supplierIds = Object.keys(state.suppliers).sort();

    if (supplierIds.length === 0) {
        container.innerHTML = '<div class="agent-placeholder">Waiting for supplier agents...</div>';
        return;
    }

    container.innerHTML = supplierIds.map(id => {
        const supplier = state.suppliers[id];

        return `
            <div class="agent-card supplier">
                <span class="agent-icon">💰</span>
                <span class="agent-name">${id}</span>
                <span class="agent-stat">Wins: ${supplier.wins || 0}</span>
                ${supplier.budget ? `<span class="agent-stat">Budget: $${supplier.budget}</span>` : ''}
            </div>
        `;
    }).join('');
}

// ============================================================
//                    BDI HANDLERS
// ============================================================

function handleBDIUpdate(data) {
    state.bdi.beliefs = data.beliefs || [];
    state.bdi.desires = data.desires || [];
    state.bdi.intentions = data.intentions || [];

    renderBDI();
}

function renderBDI() {
    const beliefsList = document.getElementById('beliefsList');
    const desiresList = document.getElementById('desiresList');
    const intentionsList = document.getElementById('intentionsList');

    beliefsList.innerHTML = state.bdi.beliefs.length
        ? state.bdi.beliefs.map(b => `<li>${b}</li>`).join('')
        : '<li>No beliefs</li>';

    desiresList.innerHTML = state.bdi.desires.length
        ? state.bdi.desires.map(d => `<li>${d}</li>`).join('')
        : '<li>No desires</li>';

    intentionsList.innerHTML = state.bdi.intentions.length
        ? state.bdi.intentions.map(i => `<li>${i}</li>`).join('')
        : '<li>No intentions</li>';
}

// ============================================================
//                    ECONOMY & PREDICTION HANDLERS
// ============================================================

function handleEconomyUpdate(data) {
    state.economy.income = data.income || 0;
    state.economy.expenses = data.expenses || 0;

    document.getElementById('totalIncome').textContent = `$${state.economy.income.toFixed(2)}`;
    document.getElementById('totalExpenses').textContent = `$${state.economy.expenses.toFixed(2)}`;
}

function handlePredictionUpdate(data) {
    state.prediction.value = data.prediction;
    state.prediction.confidence = data.confidence;
    state.prediction.samples = data.samples;

    document.getElementById('predictionValue').textContent = data.prediction || '--';
    document.getElementById('predictionConfidence').textContent = `${data.confidence || 0}%`;
    document.getElementById('predictionSamples').textContent = data.samples || '--';
}

// ============================================================
//                    OTHER HANDLERS
// ============================================================

function handleWeatherUpdate(data, timestamp) {
    addLog('marketLog', 'weather', `🌤️ Weather: ${data.condition || 'unknown'}`, timestamp);
}

function handleAgentStart(data, timestamp) {
    const agent = data.agent;

    if (agent.includes('Drone')) {
        state.drones[agent] = { state: 'idle', target: null };
        renderDrones();
    } else if (agent.includes('Harvester')) {
        state.harvesters[agent] = { state: 'idle', target: null };
        renderHarvesters();
    } else if (agent.includes('Supplier')) {
        state.suppliers[agent] = { wins: 0, totalSpent: 0 };
        renderSuppliers();
    }

    addLog('marketLog', 'system', `🟢 ${agent} started`, timestamp);
}

function handleAgentStop(data, timestamp) {
    const agent = data.agent;
    addLog('marketLog', 'system', `🔴 ${agent} stopped`, timestamp);
}

function handleAgentInteraction(data, timestamp) {
    const { from, to, messageType, content } = data;

    // Route to appropriate log
    if (from.includes('Drone') || to.includes('Drone')) {
        addLog('droneLog', 'msg', `${from} → ${to}: ${content}`, timestamp);
    } else if (from.includes('Harvester') || to.includes('Harvester')) {
        addLog('harvesterLog', 'msg', `${from} → ${to}: ${content}`, timestamp);
    } else {
        addLog('marketLog', 'msg', `${from} → ${to}: ${content}`, timestamp);
    }
}

function handleSensorAlert(data, timestamp) {
    addLog('droneLog', 'alert', `⚠️ Sensor alert: Field ${data.fieldId} - ${data.message || 'low moisture'}`, timestamp);
}

// ============================================================
//                    LOG SYSTEM
// ============================================================

const MAX_LOG_ENTRIES = 50;

function addLog(logId, type, message, timestamp) {
    const container = document.getElementById(logId);
    if (!container) return;

    const time = timestamp
        ? new Date(timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString();

    const entry = document.createElement('div');
    entry.className = `log-entry ${type}`;
    entry.innerHTML = `
        <span class="log-time">${time}</span>
        <span class="log-message">${message}</span>
    `;

    // Add to top
    container.insertBefore(entry, container.firstChild);

    // Limit entries
    while (container.children.length > MAX_LOG_ENTRIES) {
        container.removeChild(container.lastChild);
    }
}

// ============================================================
//                    INITIALIZATION
// ============================================================

document.addEventListener('DOMContentLoaded', () => {
    console.log('[App] Smart Farm V2 Frontend Starting...');
    console.log('[App] Note: This is a DISPLAY-ONLY frontend.');
    console.log('[App] All state comes from backend agents via WebSocket.');

    // Connect to backend
    connect();
});
