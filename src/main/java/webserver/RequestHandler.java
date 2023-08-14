package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.

        	InputStreamReader ir = new InputStreamReader(in);
            BufferedReader br = new BufferedReader(ir);
            String line = br.readLine();
            if (line == null) {
                return;
            }
            
            // 요청 url 을 잘라 파일 경로르 추춣한다.
            String[] tokens = line.split(" ");
            String url = tokens[1];
            log.info("URL: {}",url);

            // url 에서 파라미터들을 분리한다. ?name=hi&age=14 이렇게 되어있기에 파라미터는 index = 1에 저장된다.
            String[] url_suffix = url.split("\\?");
            log.info("url_suffix[0]: {}",url_suffix[0]);
            // 파라미터가 있을 때의 조건


            // 만약 요청이 null 이면 종료
            if (line == null) return;

            int contentLength = 0;

            while(!"".equals(line)){
                if(line.contains("Content-Length")){
                    String[] lengthArray = line.split(" ");
                    contentLength = Integer.parseInt(lengthArray[1]);
                }
                log.info("{}",line);
                line = br.readLine();
            }

            // 회원가입 요청이 들어올 때
            if (url_suffix[0].equals("/user/create") ) {
                String bodyData = IOUtils.readData(br, contentLength);
                log.info("bodyData: {}",bodyData);
                Map<String, String> paramMap = HttpRequestUtils.parseQueryString(bodyData);

                String userId = (String) paramMap.get("userId");
                String password = (String) paramMap.get("password");
                String name = (String) paramMap.get("name");
                String email = (String) paramMap.get("email");

                User user = new User(userId, password, name, email);
                log.info("User : {}", user);
            }

            // OutStream을 통해 응답 출력
            DataOutputStream dos = new DataOutputStream(out);

            if (url_suffix[0].equals("/user/create") ) {
                response302Header(dos);
            }else{
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                log.info("Path: {}",new File("./webapp" + url).toPath());
                response200Header(dos, body.length);
                responseBody(dos, body);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
   private void response302Header(DataOutputStream dos) {
        try {
            String location = "/index.html";

            dos.writeBytes("HTTP/1.1 200 Found \r\n");
            dos.writeBytes("Location: "+ location);
            dos.writeBytes("\r\n");

            log.info("Location : {}", location);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

}
