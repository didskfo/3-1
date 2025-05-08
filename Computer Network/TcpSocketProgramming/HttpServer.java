import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HttpServer {
    private static final int PORT = 7070;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(PORT + "번 포트에서 서버 실행되는 중");

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            // 지속 연결
            while (true) {
                // Request 첫 줄 읽기
                String request = br.readLine();
                if (request == null) break;
                if (request.isEmpty()) continue;

                System.out.println("클라이언트 요청: " + request);
                String[] part = request.split(" ");
                String method = part[0];
                String path = part[1];

                int contentLength = 0;
                String line;

                // 헤더 읽기 
                while ((line = br.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(line.split(" ")[1].trim());
                        } catch (NumberFormatException e) {
                            contentLength = 0;
                        }
                    }
                }

                // GET USERS BY KEY AND VALUE REQUEST
                if (method.equals("GET") && path.startsWith("/users?")) {
                    handleGetUsersByKeyValue(writer, path);
                }

                // GET ALL USERS REQUEST
                else if (method.equals("GET") && path.equals("/users")) {
                    handleGetAllUsers(writer);
                }

                // GET (BAD REQUEST)
                else if (method.equals("GET")) {
                    sendHttpResponse(writer, 400, "Bad Request", "지원하지 않는 요청입니다.");
                }

                // HEAD 쿼리 파라미터가 포함된 경우 REQUEST
                else if (method.equals("HEAD") && path.startsWith("/users?")) {
                    handleHeadUsersByKeyValue(writer, path);
                }

                // HEAD 전체 사용자 조회 REQUEST
                else if (method.equals("HEAD") && path.equals("/users")) {
                    handleHeadAllUsers(writer);
                }

                // POST (BAD REQUEST)
                else if (method.equals("POST") && !path.equals("/users")) {
                    sendHttpResponse(writer, 400, "Bad Request", "지원하지 않는 요청입니다.");
                }

                // POST USER REQUEST
                else if (method.equals("POST") && path.equals("/users")) {
                    handlePostUser(br, writer, contentLength);
                }

                // PUT 사용자 정보 수정 REQUEST
                else if (method.equals("PUT") && path.equals("/users")) {
                    handlePutUser(br, writer, contentLength);
                }

                // DELETE USER REQUEST
                else if (method.equals("DELETE") && path.startsWith("/users/")) {
                    String id = path.substring("/users/".length());
                    handleDeleteUser(writer, id);
                }

                // NOT ALLOWED METHOD
                else {
                    sendHttpResponse(writer, 405, "Method Not Allowed", "지원되지 않는 메서드입니다.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // HTTP 응답 처리 함수 (본문 전송)
    private static void sendHttpResponse(PrintWriter writer, int statusCode, String statusText, String body) {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")));
        writer.println("HTTP/1.1 " + statusCode + " " + statusText);
        writer.println("Date: " + date);             
        writer.println("Content-Type: text/plain");
        writer.println("Content-Length: " + body.length());
        writer.println(); // 헤더와 본문 사이 빈 줄
        writer.println(body);
    }

    // HEAD 응답 처리 함수 (본문은 보내지 않고 헤더만 전송)
    private static void sendHttpHeadResponse(PrintWriter writer, int statusCode, String statusText, String body) {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")));
        writer.println("HTTP/1.1 " + statusCode + " " + statusText);
        writer.println("Date: " + date);          
        writer.println("Content-Type: text/plain");
        writer.println("Content-Length: " + body.length());
        writer.println(); // 헤더와 본문 사이 빈 줄
        // HEAD 방식은 본문을 전송하지 않음
    }

    // GET /users?key=value 요청 처리 함수
    private static void handleGetUsersByKeyValue(PrintWriter writer, String path) {
        // ex) /users?name=kim
        String query = path.substring(path.indexOf("?") + 1).trim();
        
        // 쿼리 파라미터가 없을 때 400 Bad Request
        if(query.isEmpty()){
            sendHttpResponse(writer, 400, "Bad Request", "쿼리 파라미터가 없습니다.");
            return;
        }
        
        // "="를 기준으로 최대 2부분만 분리 (ex) name=kim)
        String[] param = query.split("=", 2);

        // 잘못된 쿼리 파라미터일 때 400 Bad Request
        if (param.length != 2 || param[0].trim().isEmpty() || param[1].trim().isEmpty()) {
            sendHttpResponse(writer, 400, "Bad Request", "잘못된 쿼리 파라미터입니다.");
            return;
        }
        
        String key = param[0].trim();
        String value = param[1].trim();
        
        File file = new File("users.txt");

        // file이 존재하지 않을 때 404 Not Found
        if (!file.exists()) {
            sendHttpResponse(writer, 404, "Not Found", "users.txt 파일을 찾을 수 없습니다.");
            return;
        }
        
        StringBuilder matchedUsers = new StringBuilder();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String fileLine;
            while ((fileLine = fileReader.readLine()) != null) {
                // 단순 문자열 포함 검사: "key":"value" 형태로 검색
                if (fileLine.contains("\"" + key + "\":") && fileLine.contains("\"" + value + "\"")) {
                    matchedUsers.append(fileLine).append("\n");
                }
            }
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "파일 처리 중 오류가 발생했습니다.");
            return;
        }
        
        //해당하는 유저가 없을 때 404 Not Found
        if (matchedUsers.length() == 0) {
            sendHttpResponse(writer, 404, "Not Found", "일치하는 사용자를 찾을 수 없습니다.");
            return;
        } else {
            sendHttpResponse(writer, 200, "OK", matchedUsers.toString());
            return;
        }
    }

    // GET /users 전체 조회 요청 처리 함수
    private static void handleGetAllUsers(PrintWriter writer) {
        File file = new File("users.txt");
        if (file.exists() && file.isFile()) {
            if (file.canRead()) {
                try {
                    String content = Files.readString(file.toPath());
                    sendHttpResponse(writer, 200, "OK", content);
                    return;
                } catch (IOException e) {
                    sendHttpResponse(writer, 500, "Internal Server Error", "파일을 읽는 중에 오류가 발생했습니다.");
                    return;
                }
            } else {
                sendHttpResponse(writer, 403, "Forbidden", "users.txt 파일에 접근할 권한이 없습니다.");
                return;
            }
        } else {
            // file이 존재하지 않을 때 404 Not Found
            sendHttpResponse(writer, 404, "Not Found", "users.txt 파일을 찾을 수 없습니다.");
            return;
        }
    }
    
    // POST /users 요청 처리 함수 (사용자 추가)
    private static void handlePostUser(BufferedReader br, PrintWriter writer, int contentLength) {
        // 요청 본문이 비어있을 때 400 Bad Request
        if (contentLength <= 0) {
            sendHttpResponse(writer, 400, "Bad Request", "요청 본문이 비어 있습니다.");
            return;
        }
        
        char[] bodyChars = new char[contentLength];
        try {
            int readChars = br.read(bodyChars, 0, contentLength);
            // 요청 본문 읽은 것과 contentLength가 다를 때 400 Bad Request
            if (readChars != contentLength) {
                sendHttpResponse(writer, 400, "Bad Request", "요청 본문을 완전히 읽지 못했습니다.");
                return;
            }
            
            String body = new String(bodyChars);
            try {
                Files.write(Paths.get("users.txt"), (body + "\n").getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                sendHttpResponse(writer, 201, "Created", "사용자 정보가 저장되었습니다.");
                return;
            } catch (IOException e) {
                sendHttpResponse(writer, 500, "Internal Server Error", "파일 저장 중 오류가 발생했습니다.");
                return;
            }
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "요청 본문을 읽는 중 오류가 발생했습니다.");
            return;
        }
    }

    // HEAD /users?key=value 요청 처리 함수 (헤더만 전송)
    private static void handleHeadUsersByKeyValue(PrintWriter writer, String path) {
        String query = path.substring(path.indexOf("?") + 1).trim();

        // 쿼리 파라미터가 없을 때 400 Bad Request
        if(query.isEmpty()){
            sendHttpHeadResponse(writer, 400, "Bad Request", "쿼리 파라미터가 없습니다.");
            return;
        }

        String[] param = query.split("=", 2);

        // 잘못된 쿼리 파라미터일 때 400 Bad Request
        if (param.length != 2 || param[0].trim().isEmpty() || param[1].trim().isEmpty()) {
            sendHttpHeadResponse(writer, 400, "Bad Request", "잘못된 쿼리 파라미터입니다.");
            return;
        }

        String key = param[0].trim();
        String value = param[1].trim();

        File file = new File("users.txt");

        //file이 존재하지 않을 때 404 Not Found
        if (!file.exists()) {
            sendHttpHeadResponse(writer, 404, "Not Found", "");
            return;
        }

        StringBuilder matchedUsers = new StringBuilder();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String fileLine;
            while ((fileLine = fileReader.readLine()) != null) {
                if (fileLine.contains("\"" + key + "\":") && fileLine.contains("\"" + value + "\"")) {
                    matchedUsers.append(fileLine).append("\n");
                }
            }
        } catch (IOException e) {
            sendHttpHeadResponse(writer, 500, "Internal Server Error", "");
            return;
        }
        
        //해당하는 유저가 없을 때 404 Not Found
        if (matchedUsers.length() == 0) {
            sendHttpHeadResponse(writer, 404, "Not Found", "");
            return;
        } else {
            sendHttpHeadResponse(writer, 200, "OK", matchedUsers.toString());
            return;
        }
    }

    // HEAD /users 전체 조회 처리 함수 (헤더만 전송)
    private static void handleHeadAllUsers(PrintWriter writer) {
        File file = new File("users.txt");
        if (file.exists() && file.isFile()) {
            if (file.canRead()) {
                try {
                    String content = Files.readString(file.toPath());
                    sendHttpHeadResponse(writer, 200, "OK", content);
                    return;
                } catch (IOException e) {
                    sendHttpHeadResponse(writer, 500, "Internal Server Error", "파일을 읽는 중에 오류가 발생했습니다.");
                    return;
                }
            } else {
                sendHttpHeadResponse(writer, 403, "Forbidden", "users.txt 파일에 접근할 권한이 없습니다.");
                return;
            }
        } else {
            // file이 존재하지 않을 때 404 Not Found
            sendHttpHeadResponse(writer, 404, "Not Found", "users.txt 파일을 찾을 수 없습니다.");
            return;
        }
    }

    // PUT /user 요청 처리 함수
    private static void handlePutUser(BufferedReader br, PrintWriter writer, int contentLength) {
        // 요청 본문이 비어있을 때 400 Bad Request
        if (contentLength <= 0) {
            sendHttpResponse(writer, 400, "Bad Request", "요청 본문이 비어 있습니다.");
            return; 
        }
        
        char[] bodyChars = new char[contentLength];
        try {
            int readChars = br.read(bodyChars, 0, contentLength);
            // 요청 본문 읽은 것과 contentLength가 다를 때 400 Bad Request
            if (readChars != contentLength) {
                sendHttpResponse(writer, 400, "Bad Request", "요청 본문을 완전히 읽지 못했습니다.");
                return; 
            }
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "요청 본문을 읽는 중 오류 발생");
            return; 
        }
        String body = new String(bodyChars);
        
        // JSON 본문에서 id 추출 ("id":"값" 형태)
        String id = extractValueFromJson(body, "id");
        // id가 없을 때 400 Bad Request
        if (id == null || id.isEmpty()) {
            sendHttpResponse(writer, 400, "Bad Request", "요청 본문에 id가 없습니다.");
            return; 
        }
        
        File file = new File("users.txt");
        //file이 존재하지 않을 때 404 Not Found
        if (!file.exists()) {
            sendHttpResponse(writer, 404, "Not Found", "users.txt 파일을 찾을 수 없습니다.");
            return; 
        }
        
        // 파일에서 id가 일치하는 줄은 수정할 정보를 모으고, 나머지는 원래 정보를 모은다
        StringBuilder fileContent = new StringBuilder();
        boolean userFound = false;
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String fileLine;
            while ((fileLine = fileReader.readLine()) != null) {
                // "id":"값" 형태로 id를 검사
                if (fileLine.contains("\"id\":\"" + id + "\"")) {
                    fileContent.append(body).append("\n");
                    userFound = true; // 해당하는 줄을 수정된 정보로 대체
                } else {
                    fileContent.append(fileLine).append("\n");
                }
            }
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "파일 읽기 중 오류 발생");
            return; 
        }
        
        // 해당 id의 유저가 없을 때 404 Not Found
        if (!userFound) {
            sendHttpResponse(writer, 404, "Not Found", "해당 id의 사용자를 찾을 수 없습니다.");
            return; 
        }
        
        // 전체 파일 내용을 업데이트 (덮어쓰기)
        try {
            Files.write(Paths.get("users.txt"), fileContent.toString().getBytes(),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            sendHttpResponse(writer, 200, "OK", "사용자 정보가 수정되었습니다.");
            return;
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "파일 저장 중 오류 발생");
            return;
        }
    }

    // DELETE /users/{id} 요청 처리 함수
    private static void handleDeleteUser(PrintWriter writer, String id) {
        File file = new File("users.txt");
        // users.txt 파일이 존재하지 않을 때 404 Not Found
        if (!file.exists()) {
            sendHttpResponse(writer, 404, "Not Found", "users.txt 파일을 찾을 수 없습니다.");
            return;
        }

        StringBuilder fileContent = new StringBuilder();
        boolean userFound = false;

        // 파일에서 id가 일치하는 줄은 생략하고, 나머지를 StringBuilder에 모은다
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String fileLine;
            while ((fileLine = fileReader.readLine()) != null) {
                if (fileLine.contains("\"id\":\"" + id + "\"")) {
                    userFound = true;  // 해당하는 줄은 생략
                } else {
                    fileContent.append(fileLine).append("\n");
                }
            }
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "파일 읽기 중 오류 발생");
            return;
        }

        // 해당 id의 사용자가 없으면 404 Not Found
        if (!userFound) {
            sendHttpResponse(writer, 404, "Not Found", "해당 id의 사용자를 찾을 수 없습니다.");
            return;
        }

        // 전체 파일 내용을 업데이트 (덮어쓰기)
        try {
            Files.write(Paths.get("users.txt"), fileContent.toString().getBytes(),
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            sendHttpResponse(writer, 200, "OK", "사용자 정보가 삭제되었습니다.");
            return;
        } catch (IOException e) {
            sendHttpResponse(writer, 500, "Internal Server Error", "파일 저장 중 오류 발생");
            return;
        }
    }


    // JSON 문자열에서 특정 key의 값 추출 함수
    private static String extractValueFromJson(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
