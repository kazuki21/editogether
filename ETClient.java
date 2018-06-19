import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

public class ETClient extends JFrame implements Runnable {
  private static final String APPNAME = "EdiTogether!"; // App名
  private static final int PORT = 8001; // 接続先ポート番号
  private final String FILESEP = "::!::"; // ファイル区切り文字列
  private final String SENDPHRASE = "Send message >> "; // 送信句
  private final String ARRIVEDPHRASE = "Arrived message << "; // 受信句
  private Socket socket; // 接続に用いるソケット
  private String myName; // ユーザーネーム
  private int myNumber; // ユーザー番号
  private Thread thread; // サーバーからの情報監視用スレッド

  // Swingコンポーネント
  private JTextArea fileEditArea; // ファイルを編集するテキストエリア
  private JTextArea userarea; // 参加者を表示するテキストエリア
  private JList userList; // クライアント(ユーザー)のリスト
  private JLabel filenameLabel; // ファイル名のラベル
  private JButton fileSelectButton; // ファイル選択ボタン
  private JButton fileSaveButton; // ファイル保存ボタン
  private JButton fileEditButton; // ファイル編集ボタン
  private String filename; // 編集しているファイル名

  public static void main(String[] args) {
    ETClient window = new ETClient();
    window.setSize(800, 800);
    window.setVisible(true);
  }

  public ETClient() {
    super(APPNAME);

    connectServer();

    JPanel filePanel = new JPanel();
    JPanel userPanel = new JPanel();
    userPanel.setPreferredSize(new Dimension(150, 800));

    fileEditArea = new JTextArea();
    fileEditArea.getDocument().addDocumentListener(new ETListener(this, socket));
    userList = new JList();
    fileSelectButton = new JButton("Select file");
    fileSaveButton = new JButton("Save file");
    fileEditButton = new JButton("Edit");

    fileSelectButton.addActionListener(new ETListener(this, socket));
    fileSelectButton.setActionCommand("select");

    fileSaveButton.addActionListener(new ETListener(this, socket));
    fileSaveButton.setActionCommand("save");
    fileSaveButton.setEnabled(false);

    fileEditButton.addActionListener(new ETListener(this, socket));
    fileEditButton.setActionCommand("edit");

    filePanel.setLayout(new FlowLayout());
    filenameLabel = new JLabel("Let's select file");
    filePanel.add(filenameLabel);
    filePanel.add(fileSelectButton);
    filePanel.add(fileSaveButton);
    filePanel.add(fileEditButton);
    
    userarea = new JTextArea();
    userarea.setEditable(false);
    userPanel.setLayout(new BorderLayout());
    userPanel.add(new JLabel("Edit Friends"), BorderLayout.NORTH);
    userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
    userPanel.add(userarea);
    
    if(myName != null) {
      setTitle(APPNAME + "  " + myName);
    } else {
      setTitle(APPNAME);
    }

    this.getContentPane().add(new JScrollPane(fileEditArea), BorderLayout.CENTER);
    this.getContentPane().add(filePanel, BorderLayout.NORTH);
    this.getContentPane().add(userPanel, BorderLayout.WEST);

    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    this.addWindowListener(new WindowAdapter() {
     
       public void windowOpened(WindowEvent e) {   
	   sendData("EnterUser");	  
      }
      public void windowClosed(WindowEvent e) {
        closeSocket(myNumber);
      }
    });

    thread = new Thread(this);
    thread.start();
  }

  public void connectServer() {
    Scanner sc = new Scanner(System.in);
    OutputStream outStream = null;
    InputStream inStream = null;
    PrintWriter out = null;
    DataInputStream dis = null;
    try {
      InetAddress addr = null;
      while(true) {
        System.out.print("Input IPAdress > ");
        try {
          addr = InetAddress.getByName(sc.next()); // IPアドレスの変換
          System.out.println("IP Adress: " + addr);
          break;
        } catch(UnknownHostException uhe) {
          System.out.println("IP Adress not found.");
        } catch(SecurityException se) {
          se.printStackTrace();
          System.exit(0);
        } catch(Exception e) {
          e.printStackTrace();
          System.exit(0);
        }
      }
      socket = new Socket(addr, PORT); // ソケット生成
      System.out.println("Socket: " + socket);
      if(socket.isBound()) {
        outStream = socket.getOutputStream();
        inStream = socket.getInputStream();
        out = new PrintWriter(
                new BufferedWriter(
                  new OutputStreamWriter(outStream)), true);
        dis = new DataInputStream(inStream);
        System.out.print("Input USERNAME > ");
        myName = sc.next();
        out.println(myName);
        myNumber = dis.readInt();
      }
    } catch(IOException ioe) {
      ioe.printStackTrace();
      System.exit(0);
    } catch(Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
  }

  @Override
  public void run() { // サーバーからの情報に対して処理
    InputStream inStream = null;
    BufferedReader in = null;
    try {
      inStream = socket.getInputStream();
      in = new BufferedReader(
            new InputStreamReader(inStream));
      while(!socket.isClosed()) {
        String words = in.readLine();
        String[] data = words.split(" ", 2);
        String msg = data[0];
        String value = (data.length <= 1 ? "" : data[1]);
        arrivedData(msg, value);
      }
    } catch(IOException ioe) {
      ioe.printStackTrace();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  void arrivedData(String msg, String value) throws IOException {
    switch(msg) {
    case "NewList":
	System.out.println(ARRIVEDPHRASE + msg);
        System.out.println();
	userarea.setText("");
	String[] users = value.split(" ");
	for(int line=0;line<users.length;line++) {
          userarea.append(users[line] + "\n");
        }
	break;
      case "CLOSE_SOCKET":
        System.out.println(ARRIVEDPHRASE + msg);
        System.out.println();
        if(socket != null) {
          socket.close();
          System.exit(0);
        }
        break;
      case "SET_FILE_CONTENT":
        System.out.println(ARRIVEDPHRASE + msg);
        System.out.println();
        fileEditArea.setText("");
        String[] lines = value.split(FILESEP, 0);
        for(String line : lines) {
          fileEditArea.append(line + "\n");
        }
        fileSaveButton.setEnabled(true);
        break;
      case "SET_FILE_INFO":
        System.out.println(ARRIVEDPHRASE + msg + " " + value);
        System.out.println();
        setFileName(value);
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

  void closeSocket(int userNumber) {
    String data = "REMOVE_USER" + " " + (String.valueOf(userNumber));
    sendData(data);
  }

  Socket getSocket() {
    return this.socket;
  }

  int getNumber() {
    return this.myNumber;
  }

  JTextArea getTextArea() {
    return this.fileEditArea;
  }

  JLabel getFileNameLabel() {
    return this.filenameLabel;
  }

  JButton getSelectButton() {
    return this.fileSelectButton;
  }

  JButton getSaveButton() {
    return this.fileSaveButton;
  }

  String getFileName() {
    return this.filename;
  }

  void setFileName(String filename) {
    this.filename = filename;
    this.filenameLabel.setText(filename);
  }
}
