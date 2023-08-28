import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket
public class WebSocketServer extends WebSocketHandler {


    private Map<String,File> urlMapper = new ConcurrentHashMap<>();
    private static Session clientSession;


    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.register(WebSocketServer.class);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Connection opened with: " + session.getRemoteAddress());
        clientSession = session;
    }

    public static void sendToClient(String message){
        if (clientSession != null && clientSession.isOpen()){
            System.out.println("sending message to chrome");
            clientSession.getRemote().sendStringByFuture(message);
        }
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        System.out.println("Received this message: " + message);


        if (message.equals("heartbeat")){
            return;
        }

        if (message.startsWith("path")){
            String path = message.substring(5);
            System.out.println("Received path from chrome: " + path);
            ProjectPath.projectPath = path;
            return;
        }

        if (ProjectPath.projectPath.equals("")){
            WebSocketServer.sendToClient("Did not specify a path for mapping");
            System.out.println("Did not specify a path for mapping");
            return;
        }

        File projectDir = new File(ProjectPath.projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()){
            System.out.println("Invalid project directory " + ProjectPath.projectPath);
            WebSocketServer.sendToClient("Invalid project directory " + ProjectPath.projectPath);
            return;
        }

        urlMapper.clear();

        processDirectory(projectDir);

        WebSocketServer.sendToClient("dictionary:");
        urlMapper.forEach((key,value) ->{
            System.out.println("key: " + key + ", value: " + value);
            WebSocketServer.sendToClient("key: " + key + ", value: " + value);
        });

        System.out.println("message before looking through: " + message);
        WebSocketServer.sendToClient("message before looking through: " + message);
        message = clean(message);
        System.out.println("message to look through: " + message);
        WebSocketServer.sendToClient("message to look through: " + message);

        try{
            String osName = System.getProperty("os.name").toLowerCase();
            String command;
            if (osName.contains("win")) {
                // Windows
                command = "idea.bat " + urlMapper.get(message);
            } else {
                // Linux or other OS
                command = "idea.sh " + urlMapper.get(message);
            }

            ProcessBuilder processBuilder;

            WebSocketServer.sendToClient("command: " + command);
            System.out.println(command);
            if (osName.contains("win")) {
                // Windows
                processBuilder = new ProcessBuilder("cmd", "/c", command);
            } else {
                // Linux or other OS
                processBuilder = new ProcessBuilder("bash", "-c", command);
            }
            // Open cmd from this path
            String userHome = System.getProperty("user.home");
            //System.out.println("usehome: " + userHome);
            File workingDirectory = new File(userHome);
            processBuilder.directory(workingDirectory);

            Process process = processBuilder.start();

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    WebSocketServer.sendToClient(line);
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();

            WebSocketServer.sendToClient("Command executed with exit code: " + exitCode);
            System.out.println("Command executed with exit code: " + exitCode);
        }catch(Exception e){
            e.printStackTrace();
            WebSocketServer.sendToClient(e.getMessage());
        }
    }


    /*private String getProjectPath(){
        Project currentProject = ProjectManager.getInstance().getDefaultProject();
        return currentProject.getBasePath();
    }*/
    public String clean(String message){
        String[] searchTextParts = message.split("http://localhost:8080")[1].split("/");
        String searchString = "";
        for(String part: searchTextParts){
            if (!part.equals("")){
                boolean found = false;
                String searching = searchString;
                searching += "/" + part;
                //System.out.println("for now searching is: " + searching);
                for(String key: urlMapper.keySet()){
                    if (key.contains(searching)){
                        //System.out.println("found a path");
                        found = true;
                        searchString += searching;
                        break;
                    }
                }
                if (!found) searchString += "/{}";
            }
        }
        return searchString;
    }
    public void processDirectory(File projectDir){
        //System.out.println("currently with: " + projectDir);
        File[] files = projectDir.listFiles();
        if (files != null){
            for (File file: files){
                if (file.isDirectory()){
                    processDirectory(file);
                }
                else if (file.isFile() && file.getName().endsWith(".java")){
                    processSourceFile(file);
                }
            }
        }
    }

    public void processSourceFile(File file){
        //System.out.println("now at: " + file);
        try(BufferedReader reader = new BufferedReader(new FileReader(file))){
            String line;
            while((line = reader.readLine()) != null){
                if ( line.contains("@RequestMapping") ||
                        line.contains("@GetMapping") ||
                        line.contains("@PostMapping") ||
                        line.contains("@PutMapping") ||
                        line.contains("@PathMapping")
                ){
                    int position = line.indexOf("\"");
                    //System.out.println("found a mapping at position " + position);
                    //System.out.println("search string is: " + line.substring(position));
                    position += 1;
                    String url = "";
                    while(line.charAt(position) != '"'){
                        url += line.charAt(position);
                        if (line.charAt(position) == '{'){
                            while(line.charAt(position) != '}'){
                                position += 1;
                            }
                            url += line.charAt(position);
                        }
                        position += 1;
                    }
                    //System.out.println("associated url is: " + url);
                    urlMapper.put(url,file);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        System.out.println("WebSocket connection closed: " + session.getRemoteAddress() + ", Code: " + statusCode + ", Reason: " + reason);
    }

    /*public static void main(String[] args) throws Exception {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8082);
        WebSocketHandler wsHandler = new WebSocketServer();
        server.setHandler(wsHandler);
        server.start();
        System.out.println("WebSocket server started. Listening on: ws://localhost:8082/websocket");
        server.join();
    }*/
}
