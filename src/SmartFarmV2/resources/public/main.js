/**
 * SmartFarm V2 - Professional Frontend Logic
 * Handles WebSocket communication, UI updates, tabs, and modals.
 */

/* ==========================================================================
   State & Initialization
   ========================================================================== */
const state = {
    connected: false,
    ws: null,
    workersAtFields: {},
    activeTab: 'overview',
    logs: []
};

document.addEventListener('DOMContentLoaded', () => {
    initWebSocket();
    initTabs();
    initModals();
    initMarket(); // Initialize Market Grid
});

/* ==========================================================================
   WebSocket Communication
   ========================================================================== */
function initWebSocket() {
    state.ws = new WebSocket('ws://localhost:8080/ws');

    state.ws.onopen = () => {
        state.connected = true;
        updateConnectionStatus(true);
        addLog('System connected', 'success');
        document.querySelector('.connection-overlay')?.classList.add('hidden');
    };

    state.ws.onclose = () => {
        state.connected = false;
        updateConnectionStatus(false);
        addLog('Connection lost - Reconnecting...', 'error');
        setTimeout(initWebSocket, 3000);
    };

    state.ws.onerror = (err) => {
        console.error('WebSocket error:', err);
    };

    state.ws.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            handleMessage(msg);
        } catch (e) {
            console.error('Message parse error:', e);
        }
    };
}

function handleMessage(msg) {
    // Console log for debug (optional)
    // console.log('RX:', msg);

    switch (msg.type) {
        case 'CONNECTED':
            addLog(msg.message, 'success');
            break;
        case 'FIELD_UPDATE':
            updateFieldUI(msg.data);
            break;
        case 'AGENT_UPDATE':
            updateAgentUI(msg.data);
            break;
        case 'AGENT_MOVE':
            handleAgentMove(msg.data);
            break;
        case 'INVENTORY_UPDATE':
            updateInventoryUI(msg.data);
            break;
        case 'TIME_UPDATE':
            updateTimeUI(msg.data);
            break;
        case 'WEATHER_UPDATE':
            updateWeatherUI(msg.data);
            break;
        case 'LOG':
            addLog(msg.data.message);
            // Also route logs if necessary
            break;
        default:
            console.warn('Unknown message type:', msg.type);
    }
}

/* ==========================================================================
   UI Updates
   ========================================================================== */
function updateConnectionStatus(isConnected) {
    const dot = document.getElementById('statusDot');
    const text = document.getElementById('statusText');
    
    if (dot && text) {
        if (isConnected) {
            dot.className = 'status-dot connected';
            text.textContent = 'Connected';
        } else {
            dot.className = 'status-dot disconnected';
            text.textContent = 'Disconnected';
        }
    }
}

function updateTimeUI(data) {
    document.getElementById('gameTime').textContent = data.display;
    
    // Day/Night Cycle
    const hour = data.hour;
    const isNight = hour >= 18 || hour < 6;
    const overlay = document.getElementById('nightOverlay');
    
    if (overlay) {
        let opacity = 0;
        if (isNight) {
            // Simple visual calculation for night darkness
            opacity = hour >= 18 ? 0.3 : 0.2;
        }
        overlay.style.opacity = opacity;
    }
}

function updateWeatherUI(data) {
    const weatherEl = document.getElementById('weatherDisplay');
    if (weatherEl) {
        weatherEl.innerHTML = `<span class="stat-label">Weather:</span> ${data.icon} ${data.name}`;
    }
}

function updateInventoryUI(data) {
    // Header Money
    if (data.money !== undefined) {
        state.money = data.money; // Update local state for checks
        document.getElementById('moneyDisplay').textContent = `$${data.money.toLocaleString()}`;
        
        // Refresh Market UI to update disabled states
        const marketTab = document.getElementById('market');
        if (marketTab && marketTab.classList.contains('active')) {
            const activeFilter = document.querySelector('.cat-btn.active')?.innerText || 'All';
            const filterMap = { 'All': 'ALL', 'Real Estate': 'LAND', 'Equipment': 'EQUIPMENT', 'Resources': 'RESOURCE' };
            renderMarket(filterMap[activeFilter] || 'ALL');
        }
    }

    // Warehouse Card
    const warehouseInfo = document.getElementById('warehouseStats');
    if (warehouseInfo) {
        warehouseInfo.innerHTML = `
            <div class="stat-row">💧 Water: <b>${data.water}</b></div>
            <div class="stat-row">🧪 Fungicide: <b>${data.fungicide}</b></div>
            <div class="stat-row">🌱 Seeds: <b>${data.seeds}</b></div>
            <div class="stat-row">📦 Crops: <b>${data.crops}</b></div>
        `;
    }
}

/* Field Updates */
function updateFieldUI(data) {
    let fieldCard = document.getElementById(`field-${data.id}`);

    // If field doesn't exist in DOM, create it dynamically (Dynamic Expansion!)
    if (!fieldCard) {
        createFieldCard(data);
        fieldCard = document.getElementById(`field-${data.id}`);
    }
    
    // Handle empty field state
    const emptyOverlay = document.getElementById(`empty-field-${data.id}`);
    const activeOverlay = document.getElementById(`active-field-${data.id}`);
    
    if (data.planted === false) {
        emptyOverlay.style.display = 'flex';
        activeOverlay.style.display = 'none';
        
        // Update Plant Button Text based on type (if we had specific crop types per field type)
        // For now, simple fallback
        const btnText = emptyOverlay.querySelector('span:last-child');
        if (btnText && !btnText.textContent.includes('Plant')) {
             btnText.textContent = `Plant ${data.crop || 'Crop'} ($500)`;
        }
        
        return; // Stop update if empty
    } else {
        emptyOverlay.style.display = 'none';
        activeOverlay.style.display = 'block';
    }

    // Update Basic Info
    document.getElementById(`crop-icon-${data.id}`).textContent = data.cropIcon || '🌱';
    document.getElementById(`crop-name-${data.id}`).textContent = data.crop || 'Unknown';
    
    // Update Progress Bars
    setProgressBar(`moisture-${data.id}`, data.moisture, 20);
    setProgressBar(`health-${data.id}`, data.health, 40);
    setProgressBar(`growth-${data.id}`, data.growth, 999); // No low warning for growth
    setProgressBar(`scan-${data.id}`, data.scanLevel, 30);

    // Update Badges (Disease / Harvest)
    const statusContainer = document.getElementById(`status-badges-${data.id}`);
    let badgesHtml = '';
    
    if (data.disease) {
        // Check for specific JSON string or raw
        let diseaseName = data.disease.replace(/"/g, '');
        if (diseaseName !== 'null') {
             badgesHtml += `<span class="badge badge-danger">🦠 ${diseaseName}</span>`;
             fieldCard.style.borderColor = 'var(--accent-red)';
        } else {
             fieldCard.style.borderColor = 'transparent';
        }
    } else {
        fieldCard.style.borderColor = 'transparent'; // Reset border
        if (data.growth >= 100) {
            badgesHtml += `<span class="badge badge-success">✨ Ready to Harvest</span>`;
            fieldCard.style.borderColor = 'var(--accent-green)';
        }
    }
    statusContainer.innerHTML = badgesHtml;
}

function createFieldCard(data) {
    const grid = document.querySelector('.dashboard-grid'); // or handle specific container if needed
    // Assuming fields are just appended to the grid
    
    const div = document.createElement('div');
    div.className = 'card card-field';
    div.id = `field-${data.id}`;
    
    div.innerHTML = `
        <div class="card-header">
            <span class="card-title">🌱 Field ${data.id}</span>
            <div id="status-badges-${data.id}" style="display:flex; gap:0.5rem"></div>
        </div>

        <!-- Empty State -->
        <div id="empty-field-${data.id}" class="action-btn-lg" onclick="plantField(${data.id})" style="display:none">
            <span class="action-icon">➕</span>
            <span>Plant Crop ($500)</span>
        </div>

        <!-- Active State -->
        <div id="active-field-${data.id}" class="field-content">
            <div class="field-header">
                <div class="field-info">
                    <h3><span id="crop-icon-${data.id}">🌱</span> <span id="crop-name-${data.id}">Unknown</span></h3>
                </div>
                <div class="workers-container" id="workers-${data.id}"></div>
            </div>

            <div class="farm-stats">
                <div class="stat-group">
                    <div class="stat-bar-header">
                        <span>💧 Moisture</span>
                        <span id="val-moisture-${data.id}">0%</span>
                    </div>
                    <div class="progress-track">
                        <div id="bar-moisture-${data.id}" class="progress-fill fill-moisture" style="width: 0%"></div>
                    </div>
                </div>

                <div class="stat-group">
                    <div class="stat-bar-header">
                        <span>❤️ Health</span>
                        <span id="val-health-${data.id}">100%</span>
                    </div>
                    <div class="progress-track">
                        <div id="bar-health-${data.id}" class="progress-fill fill-health" style="width: 100%"></div>
                    </div>
                </div>

                <div class="stat-group">
                    <div class="stat-bar-header">
                        <span>📈 Growth</span>
                        <span id="val-growth-${data.id}">0%</span>
                    </div>
                    <div class="progress-track">
                        <div id="bar-growth-${data.id}" class="progress-fill fill-growth" style="width: 0%"></div>
                    </div>
                </div>

                <div class="stat-group">
                    <div class="stat-bar-header">
                        <span>🔍 Scan Level</span>
                        <span id="val-scan-${data.id}">100%</span>
                    </div>
                    <div class="progress-track">
                        <div id="bar-scan-${data.id}" class="progress-fill fill-scan" style="width: 100%"></div>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    grid.appendChild(div);
    addLog(`New Field detected: Field-${data.id}`, 'success');
}

function setProgressBar(id, value, lowThreshold) {
    const bar = document.getElementById(`bar-${id}`);
    const text = document.getElementById(`val-${id}`);
    
    if (bar && text) {
        bar.style.width = `${value}%`;
        text.textContent = `${value}%`;
        
        // Color logic based on thresholds (simple version)
        if (value <= lowThreshold) {
            // Could add 'bg-red' class here if desired
        }
    }
}

/* Agent Updates */
function updateAgentUI(data) {
    // 1. Update Agent Grid (Agents Tab)
    let agentCard = document.getElementById(`agent-card-${data.id}`);
    if (!agentCard) {
        createAgentCard(data);
        agentCard = document.getElementById(`agent-card-${data.id}`);
    }

    if (agentCard) {
        const locationSpan = agentCard.querySelector('.agent-location');
        const statusSpan = agentCard.querySelector('.agent-state');
        const batBar = agentCard.querySelector('.battery-fill');
        
        const locName = data.location.replace('-Container', '');
        locationSpan.textContent = locName;
        statusSpan.textContent = data.status;
        
        batBar.style.width = `${data.battery}%`;
        
        // Battery color
        if (data.battery < 20) batBar.style.backgroundColor = 'var(--accent-red)';
        else if (data.battery < 50) batBar.style.backgroundColor = 'var(--accent-orange)';
        else batBar.style.backgroundColor = 'var(--accent-green)';
    }

    // 2. Update Workers on Field (Overview)
    updateWorkerLocation(data.id, data.location);

    // 3. Update Monitor Widget (Bottom Right)
    updateMonitorCard(data);
}

function updateMonitorCard(data) {
    const container = document.getElementById('agent-monitor');
    if (!container) return; // Should exist

    let card = document.getElementById(`monitor-card-${data.id}`);
    
    // Create if new
    if (!card) {
        // Only for mobile agents or agents with battery
        if (!['drone', 'sprayer', 'irrigator', 'harvester'].includes(data.type)) return;

        card = document.createElement('div');
        card.id = `monitor-card-${data.id}`;
        card.className = 'agent-card-mini';
        
        let icon = '🤖';
        if (data.type === 'sprayer') icon = '🚿';
        if (data.type === 'irrigator') icon = '💧';
        if (data.type === 'harvester') icon = '🚜';

        // SVG Ring Structure
        card.innerHTML = `
            <div class="agent-icon-wrapper">
                <svg class="progress-ring-svg">
                    <circle class="progress-ring-circle-bg" cx="22" cy="22" r="20"></circle>
                    <circle class="progress-ring-circle" cx="22" cy="22" r="20"></circle>
                </svg>
                <div class="agent-icon-mini">${icon}</div>
            </div>
            <div class="agent-info-mini">
                <span class="agent-name-mini">${data.id}</span>
                <span class="agent-status-mini">${data.status}</span>
            </div>
        `;
        container.appendChild(card);
    }

    // Update Data
    if (card) {
        const circle = card.querySelector('.progress-ring-circle');
        const statusText = card.querySelector('.agent-status-mini');
        
        // Status Text
        statusText.textContent = data.status;

        // Ring Progress
        const radius = 20;
        const circumference = 2 * Math.PI * radius; // ~125.6
        const percent = data.battery || 0;
        const offset = circumference - (percent / 100) * circumference;
        
        circle.style.strokeDasharray = `${circumference} ${circumference}`;
        circle.style.strokeDashoffset = offset;

        // Color Logic
        circle.classList.remove('warn', 'danger');
        if (percent < 30) circle.classList.add('danger');
        else if (percent < 60) circle.classList.add('warn');
    }
}

function createAgentCard(data) {
    const container = document.getElementById('agentsGrid');
    if (!container) return;

    const icon = data.type === 'drone' ? '🤖' : '🚜';
    const card = document.createElement('div');
    card.className = 'agent-card-item';
    card.id = `agent-card-${data.id}`;
    
    card.innerHTML = `
        <div class="agent-avatar">${icon}</div>
        <div class="agent-info" style="flex:1">
            <h4>${data.id}</h4>
            <div class="agent-status-text">
                📍 <span class="agent-location">${data.location}</span>
                • <span class="agent-state">${data.status}</span>
            </div>
            <div class="progress-track" style="margin-top:0.5rem; height:4px">
                <div class="progress-fill battery-fill" style="width:${data.battery}%; background-color:var(--accent-green)"></div>
            </div>
        </div>
    `;
    
    container.appendChild(card);
}

function updateWorkerLocation(agentId, location) {
    // Remove from old trackers
    for (let i = 1; i <= 20; i++) { // Support up to 20 fields dynamically? Check DOM.
        const fieldWorkers = document.getElementById(`workers-${i}`);
        if (fieldWorkers) {
            const existing = fieldWorkers.querySelector(`[data-agent="${agentId}"]`);
            if (existing) existing.remove();
        }
    }

    // Add to new tracker if it's a field
    if (location.startsWith('Field-Container-')) {
        const fieldId = location.replace('Field-Container-', '');
        const targetContainer = document.getElementById(`workers-${fieldId}`);
        if (targetContainer) {
            const badge = document.createElement('div');
            badge.className = 'worker-badge';
            badge.setAttribute('data-agent', agentId);
            
            let icon = '🤖';
            if (agentId.includes('Irrigator')) icon = '💧';
            else if (agentId.includes('Harvester')) icon = '🌾';
            else if (agentId.includes('Sprayer')) icon = '💉';
            
            badge.innerHTML = `${icon} <span>${agentId.split('-')[0]}</span>`;
            targetContainer.appendChild(badge);
        }
    }
}

function handleAgentMove(data) {
    addLog(`${data.agent} moving: ${data.from} -> ${data.to}`);
}

/* ==========================================================================
   MARKET LOGIC (Redesign)
   ========================================================================== */

const MARKET_ITEMS = [
    // LAND
    { id: 'WHEAT_FIELD', type: 'LAND', name: 'Wheat Field', icon: '🌾', price: 1000, desc: '', cmd: 'WHEAT', category: 'LAND' },
    { id: 'CORN_FIELD', type: 'LAND', name: 'Corn Field', icon: '🌽', price: 1500, desc: '', cmd: 'CORN', category: 'LAND' },
    
    // EQUIPMENT
    { id: 'SMART_DRONE', type: 'ITEM', name: 'Better Drone', icon: '🤖', price: 2000, desc: '+50% Speed.', cmd: 'SMART_DRONE', category: 'EQUIPMENT' },
    
    // UPGRADES
    { id: 'WATER_OPT', type: 'ITEM', name: 'Water Saver', icon: '💧', price: 1800, desc: '-20% Water usage for all fields.', cmd: 'WATER_OPTIMIZER', category: 'UPGRADE' },
    { id: 'SPEED_CHIP', type: 'ITEM', name: 'Overclocking', icon: '⚡', price: 2500, desc: '+10% Speed for all agents.', cmd: 'SPEED_BOOST', category: 'UPGRADE' }
];

let selectedItem = null;

function initMarket() {
    renderMarket('ALL');
}

function filterMarket(category) {
    // Update Tabs
    document.querySelectorAll('.cat-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');

    renderMarket(category);
}

function renderMarket(filter) {
    const grid = document.getElementById('marketGrid');
    if (!grid) return;
    
    grid.innerHTML = '';

    const items = filter === 'ALL' 
        ? MARKET_ITEMS 
        : MARKET_ITEMS.filter(i => {
            if (filter === 'LAND') return i.category === 'LAND';
            if (filter === 'EQUIPMENT') return i.category === 'EQUIPMENT';
            return i.category === 'UPGRADE'; // Resource fallback
        });

    items.forEach(item => {
        const canAfford = state.money >= item.price;
        const typeClass = item.category === 'LAND' ? 'type-land' : (item.category === 'EQUIPMENT' ? 'type-equipment' : 'type-upgrade');
        
        const card = document.createElement('div');
        card.className = `market-card ${typeClass}`;
        card.innerHTML = `
            <div class="market-icon">${item.icon}</div>
            <div class="market-info">
                <h4>${item.name}</h4>
                <div class="market-price-tag">$${item.price}</div>
                <p class="market-desc">${item.desc}</p>
            </div>
            <button class="btn ${canAfford ? 'btn-primary' : 'btn-secondary'}" 
                ${canAfford ? '' : 'disabled'}
                onclick="openPurchaseModal('${item.id}')">
                ${canAfford ? 'Buy Now' : 'Insuff. Funds'}
            </button>
        `;
        grid.appendChild(card);
    });
    
    // Update Balance Display
    const balDisplay = document.getElementById('marketMoneyDisplay');
    if (balDisplay) balDisplay.textContent = `$${state.money}`;
}

function openPurchaseModal(itemId) {
    selectedItem = MARKET_ITEMS.find(i => i.id === itemId);
    if (!selectedItem) return;

    // Populate Modal
    document.getElementById('modalItemIcon').textContent = selectedItem.icon;
    document.getElementById('modalItemName').textContent = selectedItem.name;
    document.getElementById('modalItemDesc').textContent = selectedItem.desc;
    document.getElementById('modalItemPrice').textContent = `$${selectedItem.price}`;
    
    document.getElementById('modalCurrentBalance').textContent = `$${state.money}`;
    const newBal = state.money - selectedItem.price;
    const newBalEl = document.getElementById('modalNewBalance');
    newBalEl.textContent = `$${newBal}`;
    newBalEl.style.color = newBal >= 0 ? 'var(--accent-green)' : 'var(--accent-red)';

    const confirmBtn = document.getElementById('confirmPurchaseBtn');
    confirmBtn.disabled = newBal < 0;

    // Show
    document.getElementById('purchaseModal').classList.add('active');
}

function executePurchase() {
    if (!selectedItem) return;
    if (state.money < selectedItem.price) return;

    if (selectedItem.type === 'LAND') {
        buyField(selectedItem.cmd); // Re-use backend specific cmd
    } else {
        buyItem(selectedItem.cmd);
    }

    closeModal('purchaseModal');
    addLog(`Purchased ${selectedItem.name} for $${selectedItem.price}`, 'success');
}

function closeModal(id) {
    document.getElementById(id).classList.remove('active');
}

/* ==========================================================================
   Interactions
   ========================================================================== */
function plantField(id) {
    if (state.connected) {
        state.ws.send(JSON.stringify({
            type: 'COMMAND',
            command: 'PLANT',
            fieldId: id,
            crop: 'CORN'
        }));
        addLog(`Requesting to plant Field ${id}...`, 'highlight');
    } else {
        alert('System not connected!');
    }
}

/* ==========================================================================
   Logs & Tabs
   ========================================================================== */
function addLog(text, type = '') {
    // 1. Main Log Panel
    const logContainer = document.getElementById('systemLogs');
    const time = new Date().toLocaleTimeString('en-US', { hour12: false });
    
    if (logContainer) {
        const row = document.createElement('div');
        row.className = 'log-entry';
        row.innerHTML = `
            <span class="log-time">[${time}]</span>
            <span class="log-msg ${type}">${text}</span>
        `;
        logContainer.insertBefore(row, logContainer.firstChild);

        // Prune logs
        if (logContainer.children.length > 50) {
            logContainer.removeChild(logContainer.lastChild);
        }
    }

    // 2. Mini Logs Overlay (Bottom Right)
    const miniContainer = document.getElementById('miniLogs');
    if (miniContainer) {
        const miniRow = document.createElement('div');
        miniRow.className = `mini-log-entry ${type}`;
        miniRow.innerHTML = `<span style="opacity:0.6">[${time}]</span> ${text}`;
        
        // Flex-direction is column-reverse in CSS? No, wait. 
        // If we want it to look like a stream at the bottom, we might want to appendChild 
        // and let it scroll or just show mostly recent.
        // Let's prepend to coincide with "newest top" or "newest bottom".
        // Usually overlays are "newest at bottom".
        // Let's prepend for consistency with main logs (newest top).
        miniContainer.insertBefore(miniRow, miniContainer.firstChild);
        
        // Prune mini logs hard (only keep 5)
        if (miniContainer.children.length > 5) {
            miniContainer.removeChild(miniContainer.lastChild);
        }
    }
}

function initTabs() {
    const links = document.querySelectorAll('.nav-item');
    links.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const targetId = link.getAttribute('href').substring(1); // remove #
            switchTab(targetId);
        });
    });
}

function switchTab(tabId) {
    // Update Nav
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    document.querySelector(`.nav-item[href="#${tabId}"]`)?.classList.add('active');

    // Update Content
    document.querySelectorAll('.tab-pane').forEach(el => el.classList.remove('active'));
    document.getElementById(tabId)?.classList.add('active');
    
    state.activeTab = tabId;
}

function initModals() {
    // Universal close handler
    document.querySelectorAll('.modal-close, .modal-overlay').forEach(el => {
        el.addEventListener('click', (e) => {
            if (e.target === el) {
                closeModal();
            }
        });
    });
}

function closeModal() {
    document.querySelectorAll('.modal-overlay').forEach(el => el.classList.remove('active'));
}

// Expose functions globally for HTML onClick
window.plantField = plantField;
window.buyField = buyField;
window.buyItem = buyItem;

/* ==========================================================================
   Market Actions
   ========================================================================== */
function buyField(type) {
    if (state.connected) {
        // Send command: {"type":"COMMAND", "command":"BUY_FIELD", "payload":"WHEAT"}
        const payload = JSON.stringify({
            type: 'COMMAND',
            command: 'BUY_FIELD',
            payload: type
        });
        state.ws.send(payload);
        addLog(`Requesting to buy ${type} Field...`, 'highlight');
    } else {
        alert('System not connected!');
    }
}

function buyItem(itemType) {
    if (state.connected) {
        // Placeholder for item purchase (backend logic for items is similar but we focused on fields first)
        const payload = JSON.stringify({
            type: 'COMMAND',
            command: 'BUY_ITEM',
            payload: itemType
        });
        state.ws.send(payload);
        addLog(`Requesting to buy ${itemType}...`, 'highlight');
    } else {
        alert('System not connected!');
    }
}
