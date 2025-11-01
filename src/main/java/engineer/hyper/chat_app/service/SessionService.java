package engineer.hyper.chat_app.service;

import engineer.hyper.chat_app.model.Session;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session startSession(String userId) {
        Session session = new Session(userId);
        sessions.put(session.getConversationId(), session);
        return session;
    }

    public Session getSession(String conversationId) {
        return sessions.get(conversationId);
    }

    public void endSession(String conversationId) {
        sessions.remove(conversationId);
    }
}
