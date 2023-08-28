import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.jetbrains.annotations.NotNull;


public class WebSocketServerPlugin extends AnAction implements ApplicationComponent {

    private Thread serverThread;
    private org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8082);
    @Override
    public void initComponent(){
        serverThread = new Thread(() ->{
            WebSocketHandler wsHandler = new WebSocketServer();
            server.setHandler(wsHandler);
            try {
                server.start();
                System.out.println("WebSocket server started. Listening on: ws://localhost:8082/websocket");
                server.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
    }

    @Override
    public void disposeComponent(){
        if (serverThread != null && serverThread.isAlive()){
            try{
                org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8082);
                server.stop();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }



    @Override
    public String getComponentName(){
        return "WebSocketServerPlugin";
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            descriptor.setTitle("Choose Project Path");
            descriptor.setDescription("Select the project path");

            // Show the file navigation dialog
            FileChooser.chooseFiles(descriptor, project, null, selectedFiles -> {
                if (!selectedFiles.isEmpty()) {
                    String userInput = selectedFiles.get(0).getPath();

                    WebSocketServer.sendToClient("path:" + userInput);
                    showNotification("Path selected", userInput, NotificationType.INFORMATION, project);
                }
            });
        }
    }

    public static void showNotification(String title, String content, NotificationType type, Project project) {
        Messages.showMessageDialog(project, content, title, Messages.getInformationIcon());
    }
}