import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {
    static Pattern urlPattern = Pattern.compile("(?i)(http://)?([-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b)([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
    final static Set<String> METHODS = Set.of("GET", "POST", "PATCH", "PUT", "DELETE");
    final static int PORT = 80;

    public static byte[] file;
    static String stid = "";

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Enter a Command or URL:");
            String inp = scanner.nextLine();
            inp = inp.trim();
            String inpp = inp.toLowerCase();

            // Command : exit
            if (inpp.equals("exit")) break;

            // Command : set stid
            if (inpp.equals("set-student-id-header")) {
                System.out.println("Enter Student ID :");
                String id = scanner.nextLine();
                stid = id.toLowerCase().trim();
            }

            // Command : remove stid
            else if (inpp.equals("remove-student-id-header")) {
                stid = "";
            }

            // URL
            else {
                Matcher urlMatcher = urlPattern.matcher(inp);

                if (urlMatcher.matches()) {     // correct format
                    System.out.println("Enter http method:");
                    System.out.println("GET\nPOST\nPUT\nPATCH\nDELETE");

                    String method = scanner.nextLine().trim().toUpperCase();
                    if (METHODS.contains(method)) {
                        System.out.println("Matches:");
                        String host = urlMatcher.group(2);
                        String resource = urlMatcher.group(3);
                        System.out.println(resource);
                        if (resource.isEmpty())
                            resource = "/";

                        if (resource.endsWith(".mkv")) {
                            downloadResource(host, resource);
                        } else {

                            try {
                                Socket socket = new Socket(host, PORT);
                                send(socket, resource, method, "");
                                Response response = receive(socket);
                                showResult(response);
                                String[] temp = resource.split("/");
                                if (response.isOk() && temp.length > 0)
                                    response.saveResult("./" + temp[temp.length - 1]);
                                else if (response.isOk())
                                    response.saveResult("./temp");

                                ArrayList<String> urls = response.getUrlsFromHtml();
                                for (String url : urls) {
                                    System.out.println(url + " founded!");
                                    if (!url.startsWith("./"))
                                        continue;
                                    File file = new File(getDirPath(url));
                                    boolean bool = file.mkdirs();
                                    if (bool) {
                                        System.out.println(getDirPath(url) + " Directory created successfully");
                                    } // else : is already created

                                }

                                for (String url : urls) {
                                    if (!url.startsWith("./"))
                                        continue;
                                    System.out.println("********************");
                                    Socket sockett = new Socket(host, PORT);
                                    resource = url.substring(1);
                                    send(sockett, resource, "GET", "");
                                    Response responsee = receive(sockett);
                                    showResult(responsee);
                                    if (responsee.isOk())
                                        responsee.saveResult(url);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        System.out.println("Bad method");
                    }
                } else {                        // wrong format
                    System.out.println("Bad URL");
                }
            }
        }
    }

    private static void downloadResource(String host, String resource) throws IOException, InterruptedException {
//        file = new byte[3 * 1000 * 1024 + 1];
        Downloader d1 = new Downloader("bytes=0-102400", host, PORT, resource, 0, 1 * 100 * 1024);
        d1.start();

//        Downloader d2 = new Downloader("bytes=1024000-2048000", host, PORT, resource, 1 * 1000 * 1024, 2 * 1000 * 1024);
//        d2.start();

        d1.join();
//        d2.join();

//        Path path = Paths.get("./movie.mkv");
//
//        Files.write(path, file);
//        System.out.println("./movie" + " saved\n");

//        Downloader d3 = new Downloader("bytes=0-1024000", host, PORT, resource, 0, 1 * 1000 * 1024);
//        d3.start();
    }

    public static void showResult(Response response) {
        System.out.println(response.toString());
        System.out.println();
        System.out.println(response.statusFeedback());
        System.out.println();
    }

    public static Response receive(Socket socket) {
        try {
            Response response = new Response();
            Scanner inScanner = new Scanner(socket.getInputStream());
            while (inScanner.hasNextLine()) {
                response.parseLine(inScanner.nextLine());
            }
            socket.close();
            return response;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static void send(Socket socket, String resource, String method, String range) {
        try {

            OutputStreamWriter output = new OutputStreamWriter(socket.getOutputStream());
            Request req = new Request(method, resource, stid, range);
            output.write(req.toString());
            output.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getDirPath(String url) {
        String[] folders = url.split("/");
        List<String> fs = Arrays.asList(folders).subList(0, folders.length-1);
        return String.join("/", fs);
    }
}

class Downloader extends Thread {
    private Thread t;
    private String range;
    private String host;
    private int PORT;
    private String resource;
    private int start;
    private int finish;

    public Downloader(String range, String host, int PORT, String resource, int start, int finish) {
        this.range = range;
        this.host = host;
        this.PORT = PORT;
        this.resource = resource;
        this.start = start;
        this.finish = finish;
    }

    @Override
    public void run() {
        System.out.println("*****************####");
        try {
            Socket socket = new Socket(host, PORT);
            Client.send(socket, resource, "GET", range);

            byte[] buffer = new byte[64];
            List<Byte> file = new ArrayList<>(0);
            boolean isInBody = false;

            try {
                InputStream inScanner = socket.getInputStream();
                int ii = 0;
                int l;
                while ((l = inScanner.read(buffer)) != -1) {
                    for (int i = 0; i < buffer.length; i++) {
                        if (!isInBody){
                            if (i < buffer.length - 3)
                                if ((char)buffer[i] == '\r' && (char)buffer[i+1] == '\n' && (char)buffer[i+2] == '\r' && (char)buffer[i+3] == '\n'){
                                    isInBody = true;
                                    i += 4;
                                    System.out.println(i + " founded");
                                }
                        } else {
                            file.add(buffer[i]);
                        }
                    }
//                    System.out.println("here : " + ii + "    :  " + l);
                    ii++;
                }
                Path path = Paths.get("./moviee.mkv");
                byte[] arr = new byte[file.size()];

//                Client.file = new byte[file.size()];
                System.out.println(file.size());
                for (int i = 0; i < file.size(); i++) {
                    arr[i] = file.get(i);
                }
                Files.write(path, arr);
                System.out.println("./movie" + " saved\n");

                socket.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start () {
        if (t == null) {
            t = new Thread(this);
            t.start ();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class Request {
    private String method;
    private String resource;
    private String stid;
    private String range;

    public Request(String method, String resource, String stid, String range) {
        this.method = method;
        this.resource = resource;
        this.stid = stid;
        this.range = range;
    }

    @Override
    public String toString() {
        String clrf = "\r\n";
        StringBuilder request = new StringBuilder();
        request.append(method).append(" ").append(resource).append(" ").append("HTTP/1.1").append(clrf);
        request.append("Accept: */*").append(clrf);
        if (!range.equals(""))
            request.append("Range: ").append(range).append(clrf);
        request.append("Connection: close").append(clrf);
        if (!stid.isEmpty())
            request.append("x-student-id: ").append(stid).append(clrf);
        request.append(clrf);
        return request.toString();
    }
}


class Response {
    private int statusCode;
    private String contentType;
    private ArrayList<String> headers;
    private String body;

    private boolean isStatusCodeSet;
    private boolean isHeaderSet;

    public Response() {
        headers = new ArrayList<>(0);
        this.body = "";
        isStatusCodeSet = false;
        isHeaderSet = false;
    }

    public void parseLine(String line){
        if (!isStatusCodeSet){
            statusCode = Integer.parseInt(line.split(" ")[1]);
            isStatusCodeSet = true;
        } else if (!isHeaderSet){
            if (line.isEmpty())
                isHeaderSet = true;
            else {
                if (line.split(" ")[0].equals("Content-Type:"))
                    contentType = line.split(" ")[1].split(";")[0];
                headers.add(line);
            }
        } else {
            body = body + line + "\n";
        }
    }

    public void saveResult(String fileName) {
        try{
            if (contentType.equals("text/plain")){
                System.out.println(body);
            } else {
                Files.writeString(Path.of(fileName), body);
                System.out.println(fileName + " saved\n");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public ArrayList<String> getUrlsFromHtml() {
        Document document = Jsoup.parse(body);
        Elements linkTags = document.select("[href]");
        ArrayList<String> res = new ArrayList<>(0);
        for (Element linkTag : linkTags) {
            String url = linkTag.attr("href");
//            if (!url.startsWith("http"))
            res.add(url);
        }
        return res;
    }

    public String statusFeedback() {
        String feedback = "";
        if (statusCode/100 == 2)
            feedback = "2xx(Success) :\n";
        else if (statusCode/100 == 4)
            feedback = "4xx(Bad request) :\n";
        else if (statusCode/100 == 5)
            feedback = "5xx(Server error) :\n";

        if (statusCode == 200){
            feedback += "200: OK";
        } else if (statusCode == 201){
            feedback += "201: Created";
        } else if (statusCode == 204){
            feedback += "204: No Content";
        } else if (statusCode == 400){
            feedback += "400: Bad Request";
        } else if (statusCode == 401){
            feedback += "401: Unauthorized (Authorization required)";
        } else if (statusCode == 403){
            feedback += "403: Forbidden";
        } else if (statusCode == 404){
            feedback += "404: Not Found";
        } else if (statusCode == 405){
            feedback += "405: Method Not Allowed";
        } else if (statusCode == 500){
            feedback += "500: Internal Server Error";
        } else if (statusCode == 501){
            feedback += "501: Not Implementedt";
        } else if (statusCode == 503){
            feedback += "503: Service Unavailable";
        } else if (statusCode == 301){
            feedback += "301: Moved Permanently";
        } else if (statusCode == 307){
            feedback += "307: Moved Temporarily";
        } else if (statusCode == 304){
            feedback += "304: Not Modified";
        }
        return feedback;
    }

    public boolean isOk() {
        return statusCode / 100 == 2;
    }

    @Override
    public String toString() {
        return "status code : " + statusCode + "\n" +
                "content type : " + contentType +
                "headers : " + headers + "\n" +
                "body : " + body;
    }
}
