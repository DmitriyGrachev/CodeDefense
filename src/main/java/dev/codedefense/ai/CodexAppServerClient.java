package dev.codedefense.ai;

import java.util.List;

public interface CodexAppServerClient extends AutoCloseable {
    void initialize(AppServerClientInfo clientInfo, boolean experimentalApi);
    AppServerThread readThread(String threadId, boolean includeTurns);
    List<AppServerThreadItem> listThreadItems(String threadId, int limit);
    @Override void close();
}
