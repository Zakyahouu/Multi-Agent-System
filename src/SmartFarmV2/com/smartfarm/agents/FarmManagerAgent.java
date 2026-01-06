package com.smartfarm.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import com.smartfarm.web.WebServer;
import com.smartfarm.Main;
import com.smartfarm.models.FieldType;

/**
 * FarmManagerAgent - "God agent" for the user.
 * Interacts with Market to buy land and spawns new field agents via Main.
 */
public class FarmManagerAgent extends Agent {

    private WebServer webServer;
    private int fieldCount = 2; // Initial fields

    @Override
    protected void setup() {
        // Enable Object-to-Agent communication (queue size 10)
        setEnabledO2ACommunication(true, 10);
        
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            webServer = (WebServer) args[0];
            if (args.length > 1) {
                fieldCount = (Integer) args[1];
            }
        }

        System.out.println("[FarmManager] Manager Active. Monitoring GUI commands.");
        addBehaviour(new GuiCommandHandler());
    }

    /**
     * Listen for commands from the WebServer (relayed to this agent)
     * NOTE: Since we don't have a direct JADE-to-Web bridge for *input* in the current Main implementation
     * (the web server runs separately), we assume the WebServer creates a dummy agent or uses O2A to send commands.
     * 
     * However, to keep it simple and within the current architecture:
     * We will make the WebServer interact with this agent via a static reference or O2A messaging.
     * 
     * For simulation purposes, we'll assume we receive ACL messages from a "Interface" agent 
     * OR we check a command queue. 
     * 
     * Let's stick to standard ACL. The WebServer should ideally send a message.
     * But since WebServer is just Jetty, we might need a bridge.
     * 
     * ACTUALLY: The existing Index.html sends WebSocket messages.
     * We need to hook into `WebServer.java` (which I haven't seen but assumed) to relay these.
     * 
     * Re-reading plan: "Handle REQUEST_CREATE_FIELD from GUI (via WebServer)."
     * Main.java passes `webServer` to agents, so agents can push TO web.
     * But Web TO Agent? existing code `handleMessage` in JS sends JSON.
     * The `WebServer` class likely needs a way to route that to JADE.
     * 
     * Let's look at `WebServer.java` later. For now, we implement the logic to HANDLE the request.
     * We'll implement a `OneShotBehaviour` that we can trigger, or a Cyclic one that checks a queue.
     * 
     * Better: We implemented a `CyclicBehaviour` listening for ACL.
     * We'll have `Main` or `WebServer` use `O2A` (Object-to-Agent) communication to pass the event 
     * by `putO2AObject`.
     */
    private class GuiCommandHandler extends CyclicBehaviour {
        @Override
        public void action() {
            // Check O2A object queue
            Object obj = myAgent.getO2AObject();
            if (obj != null && obj instanceof String) {
                String cmd = (String) obj;
                System.out.println("[FarmManager] Received GUI Command: " + cmd);
                
                if (cmd.startsWith("BUY_FIELD:")) {
                   purchaseField(cmd.substring(10));
                }
            } else {
                block();
            }
        }
    }
    
    private void purchaseField(String typeStr) {
        // 1. Ask Market
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(new AID("Market", AID.ISLOCALNAME));
        request.setContent("BUY_FIELD:" + typeStr);
        send(request);
        
        // 2. Wait for Reply (Blocking for simplicity in this flow, or use FSM in production)
        ACLMessage reply = blockingReceive(MessageTemplate.MatchSender(new AID("Market", AID.ISLOCALNAME)));
        
        if (reply != null && reply.getPerformative() == ACLMessage.CONFIRM) {
            // Approved! Create Field.
            createField(typeStr);
        } else {
            System.out.println("[FarmManager] Purchase failed: " + (reply != null ? reply.getContent() : "Timeout"));
        }
    }
    
    private void createField(String typeStr) {
        try {
            FieldType type = FieldType.valueOf(typeStr);
            fieldCount++;
            int newId = fieldCount;
            
            System.out.println("[FarmManager] Deploying new Field-" + newId + " (" + typeStr + ")");
            
            // Invoke static helper in Main to handle JADE container logic
            Main.createFieldAgent(newId, type.getCropType());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
