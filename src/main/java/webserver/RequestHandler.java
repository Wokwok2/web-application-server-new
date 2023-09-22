package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

import static util.HttpRequestUtils.parseCookies;

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
            Map<String, String> cookies = new HashMap<>();



            while(!"".equals(line)){
                if(line.contains("Content-Length")){
                    String[] lengthArray = line.split(" ");
                    contentLength = Integer.parseInt(lengthArray[1]);
                }
                // 요청 헤더에 Cookie 라는 텍스트가 들어있을 때
                // Cookie 에 들어있는 값 들을 추출한다.
                if(line.contains("Cookie")){
                    String[] cookieValues = line.split(":");

                    String cookieValue = cookieValues[1];
                    cookies = parseCookies(cookieValue);

                    log.info("cookies : {}",cookies);
                }
                log.info("{}",line);
                line = br.readLine();
            }

            String cookie = "";

            // 회원가입 요청이 들어올 때
            if (url_suffix[0].equals("/user/create") ) {
                String bodyData = IOUtils.readData(br, contentLength);
                Map<String, String> paramMap = HttpRequestUtils.parseQueryString(bodyData);
                log.info("paramMap: {}",paramMap);

                String userId = (String) paramMap.get("userId");
                String password = (String) paramMap.get("password");
                String name = (String) paramMap.get("name");
                String email = (String) paramMap.get("email");

                User user = new User(userId, password, name, email);
                log.info("User : {}", user);
                DataBase.addUser(user);
            }
            // 로그인 요청이 들어올 때
            else if (url_suffix[0].equals("/user/login") ) {
                // body 에 들어있는 id 와 pw 를 읽기 위해 br 와 contentLength 를 파라미터로 넣어줍니다.
                // br 에서 contentLength 만큼 읽은 데이터가 body 안에 들어 있는 데이터이기 때문입니다.
                String bodyData = IOUtils.readData(br, contentLength);
                Map<String, String> paramMap = HttpRequestUtils.parseQueryString(bodyData);

                if (DataBase.findUserById(paramMap.get("userId")) != null) {
                    User findUser = DataBase.findUserById(paramMap.get("userId"));

                    // 로그인 성공 시 쿠키에 logined=true 값을 추가하기 위해 세팅
                    if (findUser.getPassword().equals(paramMap.get("password"))) {
                        cookie = "logined=true";
                    }
                    // 로그인 실패 시 쿠키에 logined=false 값을 추가하기 위해 세팅
                    else{
                        cookie = "logined=false";
                    }
                }else{
                    log.info("fail");
                    cookie = "logined=false";
                }
            }

            // /user/list.html 로 요청이 들어왔을 때
            // Map 데이터인 cookies 의 logined 의 값이 false이면 url 을 login.html로 보낸다.
//            else if (url_suffix[0].equals("/user/list.html")) {
//                if (cookies.get("logined").equals("false")) {
//                    url = "/user/login.html";
//                }
//            }

            // OutStream을 통해 응답 출력
            DataOutputStream dos = new DataOutputStream(out);

            // 회원가입 요청이 들어왔을 때  index.html 리다이렉트
            if (url_suffix[0].equals("/user/create") ) {
                response302Header(dos);
            }
            // 로그인 요청이 들어왔을 때
            // 헤더의 쿠키에 logined= 값을 세팅해준 후 index.html 로 리다이렉트
            else if (url_suffix[0].equals("/user/login") ) {
                log.info("cookie: {}",cookie);
                response302Header(dos,cookie);
            }
            // /user/list.html 로 요청이 오고 logined 쿠키 값이 false 일 때
            else if (url_suffix[0].equals("/user/list.html") && cookies.get("logined").equals("false")) {
                response302Header(dos);
            }
            // 일반 응답 헤더 생성
            else{
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                log.info("Path: {}",new File("./webapp" + url).toPath());
                response200Header(dos, body.length);
                responseBody(dos, body);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200HeaderCookie(DataOutputStream dos, String cookie, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            dos.writeBytes("\r\n");
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

    // 302 리다이렉션을 보내는 메소드
   private void response302Header(DataOutputStream dos) {
        try {
            String location = "/index.html";

            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: "+ location);
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    // 쿠키 값을 세팅 후 302 리다이렉션을 보내는 메소드
    private void response302Header(DataOutputStream dos, String cookie) {
        try {
            String location = "/index.html";

            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: "+ location+"\r\n");
            dos.writeBytes("Set-Cookie: " + cookie);
            dos.writeBytes("\r\n");
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
