import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

public class ETServer {
  private static final int PORT = 8001; // 待受ポート番号
  private ServerSocket servSock; // サーバーソケット
  private Map<Integer, ETUser> userMap; // 接続しているクライアントユーザーのハッシュマップ
  private int userCount; // 接続したクライアントの数

  // シングルトンパターン
  // ETServerインスタンスはただ1つ
  private static ETServer servInstance = new ETServer();
  private ETServer() { // プライベートコンストラクタ
    userMap = new HashMap<Integer, ETUser>();
    userCount = 0;
  }
  public static ETServer getServerInstance() {
    return servInstance;
  }

  public static void main(String[] args) {
    ETServer server = ETServer.getServerInstance();
    try {
      server.start();
    } catch(IOException ioe) {
      ioe.printStackTrace();
    }
  }

  public void start() throws IOException {
    InputStream inStream = null;
    OutputStream outStream = null;
    BufferedReader in = null;
    DataOutputStream dos = null;
    try {
      servSock = new ServerSocket(PORT);
      System.out.println("Started: " + servSock);
      System.out.println();
      while(!servSock.isClosed()) {
        Socket clientSock = servSock.accept(); // クライアントからの接続待機
        System.out.println("Connection accepted: " + clientSock);
        inStream = clientSock.getInputStream();
        outStream = clientSock.getOutputStream();
        in = new BufferedReader(
              new InputStreamReader(inStream));
        dos = new DataOutputStream(outStream);

        String name = in.readLine();
        if(name != null) {
          ETUser user = new ETUser(clientSock, name);
          addUser(clientSock, user);
          dos.writeInt(userCount);
        }
      }
    } catch(IllegalArgumentException iae) {
      System.out.println("Port parameter is illegal");
    }
  }

  void addUser(Socket socket, ETUser user) {
    if(userMap.containsValue(user)) return;
    userCount++;
    userMap.put(userCount, user);
    System.out.println("O " + user.getName() + " came! " + "[" + user + "]");
    displayUserList();
  }

  void removeUser(int userNo) {
    ETUser user = userMap.get(userNo);
    System.out.println("X " + user.getName() + " disappear... " + "[" + user + "]");
    userMap.remove(userNo);
    displayUserList();
    user.isRemoved = true;
  }

  void displayUserList() {
    System.out.println("<User List>");
    for(String username : getUserNameList()) {
      System.out.print(username + ", ");
    }
    System.out.println();
    System.out.println();
  }

  void shareInsertStr(ETUser self, int offset, String insertStr) {

  }

  HashMap<Integer, ETUser> getUserMap() {
    return (HashMap<Integer, ETUser>)userMap;
  }

  List<String> getUserNameList() {
    return userMap.values().stream()
                           .map(user -> user.getName())
                           .collect(Collectors.toList());
  }
}

class ETUser implements Runnable {
  private Socket socket; // ソケット
  private String name; // ユーザーネーム
  public boolean isRemoved; // サーバがユーザを削除したかどうか
  private ETServer server = ETServer.getServerInstance(); // サーバー
  private List<EditAreaListener> listeners; // リスナの可変長配列
  private final String SENDPHRASE = "Send message >> "; // 送信句
  private final String ARRIVEDPHRASE = "Arrived message << "; // 受信句

  public ETUser(Socket clientSock, String name) {
    this.socket = clientSock;
    this.name = name;
    this.isRemoved = false;

    Thread thread = new Thread(this);
    thread.start();
  }

  @Override
  public void run() { // クライアントからの情報に対して処理
    InputStream inStream = null;
    BufferedReader in = null;
    try {
      inStream = socket.getInputStream();
      in = new BufferedReader(
            new InputStreamReader(inStream));
      while(!socket.isClosed()) {
        String words = in.readLine();
        if(words != null) {
          String[] data = words.split(" ", 2);
          String msg = data[0];
          String value = (data.length <= 1 ? "" : data[1]);
          arrivedData(msg, value);
        }
      }
    } catch(IOException ioe) {
      ioe.printStackTrace();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  void arrivedData(String msg, String value) throws IOException {
    switch(msg) {
    case "EnterUser":
	System.out.println("from " + getName());
        System.out.println(ARRIVEDPHRASE + msg);
	System.out.println();
	String userNameList;
	userNameList=String.join(" ", server.getUserNameList());
	shareAll2("NewList",userNameList,true);
	break;
      case "REMOVE_USER":
        System.out.println("from " + getName());
        System.out.println(ARRIVEDPHRASE + msg);
        System.out.println();
        int userNo = Integer.parseInt(value);
        server.removeUser(userNo);
	userNameList=String.join(" ", server.getUserNameList());
	shareOthers2("NewList",userNameList,true);
        if(isRemoved) {
          sendData("CLOSE_SOCKET");
          this.socket.close();
        }
        break;
      case "SHARE_FILE_CONTENT":
        System.out.println("from " + getName());
        System.out.println(ARRIVEDPHRASE + msg);
        System.out.println();
        shareOthers("SET_FILE_CONTENT", value, false);
        break;
      case "SHARE_FILE_INFO":
        System.out.println("from " + getName());
        System.out.println(ARRIVEDPHRASE + msg + " " + value);
        System.out.println();
        shareOthers("SET_FILE_INFO", value, true);
        break;
      default:
        System.out.println("DON'T MATCH");
    }
  }

  void sendData(String data) {
    OutputStream outStream = null;
    PrintWriter out = null;
    System.out.println(SENDPHRASE + data);
    System.out.println();
    try {
      outStream = socket.getOutputStream();
      out = new PrintWriter(
              new BufferedWriter(
                new OutputStreamWriter(outStream)), true);
      out.println(data);
    } catch(IOException ioe) {
      ioe.printStackTrace();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  void sendDataTo(Socket toSocket, String data) {
    OutputStream outStream = null;
    PrintWriter out = null;
    try {
      outStream = toSocket.getOutputStream();
      out = new PrintWriter(
              new BufferedWriter(
                new OutputStreamWriter(outStream)), true);
      out.println(data);
    } catch(IOException ioe) {
      ioe.printStackTrace();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  void shareOthers(String msg, String value, boolean detail) {
    for(ETUser user : server.getUserMap().values()) {
      if(user != this) {
        String data = msg + " " + value;
        if(detail) System.out.println(SENDPHRASE + data + "\n");
        else System.out.println(SENDPHRASE + msg + "\n");
        sendDataTo(user.getSocket(), data);
      }
    }
  }
    void shareOthers2(String msg, String value, boolean detail) {
	String list[]=value.split(" ");
	for(ETUser user : server.getUserMap().values()) {
	    if(user!=this){
		String data=msg+" ";
		String name=user.getName();
		data +=name+"<<you>>"+" ";
		for(int i=0;i<list.length;i++){
		    if(name.equals(list[i])){}
		    else {
			data +=list[i]+" ";
		    }	
		}
		if(detail) System.out.println(SENDPHRASE + data + "\n");
		else System.out.println(SENDPHRASE + msg + "\n");
		sendDataTo(user.getSocket(), data);	
	    }
	}
    }
     void shareAll2(String msg, String value, boolean detail) {
	String list[]=value.split(" ");
	for(ETUser user : server.getUserMap().values()) {
	    String data=msg+" ";
	    String name=user.getName();
	    data +=name+"<<you>>"+" ";
	    for(int i=0;i<list.length;i++){
		if(name.equals(list[i])){}
		else {
		    data +=list[i]+" ";
		}	
	    }
	    if(detail) System.out.println(SENDPHRASE + data + "\n");
	    else System.out.println(SENDPHRASE + msg + "\n");
	    sendDataTo(user.getSocket(), data);	
	}
     }

    
    void shareAll(String msg, String value, boolean detail) {
	for(ETUser user : server.getUserMap().values()) {
	    String data = msg + " " + value;
	    if(detail) System.out.println(SENDPHRASE + data + "\n");
	    else System.out.println(SENDPHRASE + msg + "\n");
	    sendDataTo(user.getSocket(), data);
	}
    }
    
  Socket getSocket() {
    return this.socket;
  }

  String getName() {
    return this.name;
  }
}
