import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

public class HttpClient {
    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 7070;

        try (Socket socket = new Socket(hostname, port)) {
            // 하나의 소켓에서 지속 연결을 이용하기 위해 PrintWriter와 BufferedReader 생성
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
            // POST /users: 사용자 추가하기 -> 성공
            sendPostRequest(writer, reader, hostname, "bae", "Seoul", "010-0101-0101", "10");
            System.out.println();
            sendPostRequest(writer, reader, hostname, "lee", "Busan", "010-1212-1212", "21");
            System.out.println();
            sendPostRequest(writer, reader, hostname, "kang", "Seoul", "010-2323-2323", "22");
            System.out.println();
            
            // GET /users: 전체 사용자 조회하기 -> 성공
            sendGetAllRequest(writer, reader, hostname);
            System.out.println();
            
            // GET /users?key=value: 조건으로 검색하기 -> 성공
            sendGetRequestWithKeyValue(writer, reader, hostname, "name", "kim");
            System.out.println();
            sendGetRequestWithKeyValue(writer, reader, hostname, "address", "Jeju");
            System.out.println();
            sendGetRequestWithKeyValue(writer, reader, hostname, "tel", "010-2222-2222");
            System.out.println();
            sendGetRequestWithKeyValue(writer, reader, hostname, "age", "22");
            System.out.println();

            // HEAD /uesrs: 전체 사용자 조회에 대한 HEAD 요청하기 -> 성공
            sendHeadRequest(writer, reader, hostname, "/users");
            System.out.println();
            
            // HEAD /users?key=value: 조건 검색에 대한 HEAD 요청하기 -> 성공
            sendHeadRequest(writer, reader, hostname, "/users?name=kim");
            System.out.println();

            // PUT /users: id에 해당되는 유저 정보 수정하는 PUT 요청하기 -> 성공
            sendPutRequest(writer, reader, hostname, "98e59ac7-c8ec-4f9f-9556-7a9de227e289", "yang", "Seoul", "010-9999-9999", "23");
            System.out.println();

            // PUT /users: 존재하지 않는 id로 유저 정보를 수정하려고 할 때 -> 404 Not Found
            sendPutRequest(writer, reader, hostname, "abcdefg", "hwang", "Gangwon", "010-1234-5678", "100");
            System.out.println();

            // DELETE /users/{id}: id에 해당되는 유저를 삭제하는 DELETE 요청하기 -> 성공
            sendDeleteRequest(writer, reader, hostname, "314b5ba8-6e32-4b5b-adef-a7a715e0ae0f");
            System.out.println();

            // GET /users: 사용자 수정, 삭제가 잘 되었는지 확인하기 위한 전체 사용자 조회 -> 성공
            sendGetAllRequest(writer, reader, hostname);
            System.out.println();

            // 잘못된 GET 쿼리: 빈 쿼리 -> 400 Bad Request
            sendCustomRequest(writer, reader, hostname, "GET /users? HTTP/1.1");
            System.out.println();
            
            // 지원되지 않는 POST 경로: POST /invalid-> 400 Bad Request 
            sendCustomRequest(writer, reader, hostname, "POST /invalid HTTP/1.1");
            System.out.println();
            
            // 지원되지 않는 메서드: PATCH /users -> 405 Method Not Allowed
            sendCustomRequest(writer, reader, hostname, "PATCH /users HTTP/1.1");
            System.out.println();
            
            System.out.println("요청 전송 완료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // USER 생성 POST 요청을 보내는 함수
    public static void sendPostRequest(PrintWriter writer, BufferedReader reader, String hostname, String name, String address, String tel, String age) throws IOException{
        String id = UUID.randomUUID().toString();

        // JSON 형식 문자열 생성
        String body = String.format(
            "{\"id\":\"%s\",\"name\":\"%s\",\"address\":\"%s\",\"tel\":\"%s\",\"age\":\"%s\"}", 
            id, name, address, tel, age
        );

        // POST 요청 작성
        writer.println("POST /users HTTP/1.1");
        writer.println("Host: " + hostname);
        writer.println("Content-Type: application/json");
        writer.println("Content-Length: " + body.length());
        writer.println("Connection: keep-alive");
        writer.println();  // 헤더와 본문 사이의 빈 줄
        writer.print(body);
        writer.flush();

        // RESPONSE 읽기
        System.out.println("[POST /users HTTP/1.1] 응답: ");
        readResponse(reader);
    }

    // ALL USERS를 GET하는 요청을 보내는 함수
    public static void sendGetAllRequest(PrintWriter writer, BufferedReader reader, String hostname) throws IOException{
        // GET 요청 작성
        writer.println("GET /users HTTP/1.1");
        writer.println("Host: " + hostname);
        writer.println("Connection: keep-alive");
        writer.println(); 

        // RESPONSE 읽기
        System.out.println("[GET /users HTTP/1.1] 응답: ");
        readResponse(reader);
    }

    // Key와 Value를 통해 조건에 맞는 USER를 GET하는 요청을 보내는 함수
    public static void sendGetRequestWithKeyValue(PrintWriter writer, BufferedReader reader, String hostname, String key, String value) throws IOException{
        // GET 요청 작성
        String requestLine = String.format("GET /users?%s=%s HTTP/1.1", key, value);
        writer.println(requestLine);
        writer.println("Host: " + hostname);
        writer.println("Connection: keep-alive");
        writer.println();  // 헤더와 본문 사이의 빈 줄
        
        // RESPONSE 읽기
        System.out.println("[" + requestLine + "] 응답: ");
        readResponse(reader);
    }

    // 임의의 HTTP 요청을 보내는 함수 (그 외의 케이스 테스트용)
    public static void sendCustomRequest(PrintWriter writer, BufferedReader reader, String hostname, String requestLine) throws IOException{
        // HTTP 요청 작성
        writer.println(requestLine);
        writer.println("Host: " + hostname);
        writer.println("Connection: keep-alive");
        writer.println();
        
        // RESPONSE 읽기
        System.out.println("[" + requestLine + "] 응답: ");
        readResponse(reader);
    }

    // HEAD 요청을 보내는 함수
    public static void sendHeadRequest(PrintWriter writer, BufferedReader reader, String hostname, String path) throws IOException{
        // HEAD 요청 작성
        writer.println("HEAD " + path + " HTTP/1.1");
        writer.println("Host: " + hostname);
        writer.println("Connection: keep-alive");
        writer.println();  // 헤더와 본문 사이의 빈 줄
        
        // RESPONSE 읽기
        System.out.println("[HEAD " + path + " HTTP/1.1] 응답: ");
        readHeadResponse(reader);
    }

    // PUT 요청을 보내는 함수
    public static void sendPutRequest(PrintWriter writer, BufferedReader reader, String hostname, String id, String name, String address, String tel, String age) throws IOException{
        // 수정할 사용자 정보를 포함하는 JSON 문자열 생성
        // id를 포함해야 합니다.
        String body = String.format(
            "{\"id\":\"%s\",\"name\":\"%s\",\"address\":\"%s\",\"tel\":\"%s\",\"age\":\"%s\"}",
            id, name, address, tel, age
        );
        
        // PUT 요청 작성
        writer.println("PUT /users HTTP/1.1");
        writer.println("Host: " + hostname);
        writer.println("Content-Type: application/json");
        writer.println("Content-Length: " + body.length());
        writer.println("Connection: keep-alive");
        writer.println(); // 헤더와 본문 사이의 빈 줄 
        writer.print(body);
        writer.flush();

        // RESPONSE 읽기
        System.out.println("[PUT /users HTTP/1.1] 응답: ");
        readResponse(reader);
    }

    // DELETE 요청을 보내는 함수 
    public static void sendDeleteRequest(PrintWriter writer, BufferedReader reader, String hostname, String id) throws IOException {
        String path = "/users/" + id;

        // DELETE 요청 작성
        writer.println("DELETE " + path + " HTTP/1.1");
        writer.println("Host: " + hostname);
        writer.println("Connection: keep-alive");
        writer.println();  // 빈 줄

        //RESPONSE 읽기
        System.out.println("[DELETE " + path + " HTTP/1.1] 응답:");
        readResponse(reader);
    }

    // RESPONSE 읽는 함수
    public static void readResponse(BufferedReader reader) throws IOException {
        // 상태 라인과 헤더 읽기
        String statusLine = reader.readLine();
        if (statusLine == null) return;
        System.out.println(statusLine);
        
        String line;
        int contentLength = 0;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            System.out.println(line);
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                } catch(NumberFormatException e) {
                    contentLength = 0;
                }
            }
        }

        System.out.println();

        // 빈 줄 후 본문 읽기 : exactly contentLength 바이트 읽기
        char[] bodyChars = new char[contentLength];
        int totalRead = 0;
        while(totalRead < contentLength) {
            int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        String responseBody = new String(bodyChars, 0, totalRead);
        System.out.println(responseBody);
    }

    // HEAD RESPONSE 읽는 함수
    public static void readHeadResponse(BufferedReader reader) throws IOException {
        // 상태 라인 읽기
        String statusLine = reader.readLine();
        if (statusLine == null) return;
        System.out.println(statusLine);

        // 헤더 읽기
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            System.out.println(line);
        }
    }
}
