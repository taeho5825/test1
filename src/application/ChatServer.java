package application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class ChatServer extends Application 
{
	ExecutorService threadPool;
	ServerSocket serverSocket;
	Vector<Client> clients = new Vector<Client>();

	class Client {
		Socket socket;

		public Client(Socket socket) {
			this.socket = socket;
			receive();
		}

		public void receive() {
			Runnable thread = new Runnable() {
				@Override
				public void run() {
					try {
						while (true) {
							InputStream in = socket.getInputStream();
							byte[] buf = new byte[100];

							int len = in.read(buf);
							if (len == -1)
								throw new IOException();
							System.out.println("[Success] " + socket.getRemoteSocketAddress() + ": "
									+ Thread.currentThread().getName());
							String msg = new String(buf, 0, len, "UTF-8");

							for (Client client : clients) {
								client.send(msg);
							}
						}
					} catch (Exception e) {
						try {
							System.out.println("[Failure] " + socket.getRemoteSocketAddress() + ": "
									+ Thread.currentThread().getName());
							clients.remove(Client.this);
							socket.close();
						} catch (Exception e2) {}
					}
				}
			};
			threadPool.submit(thread);
		}

		public void send(String msg) {
			Runnable thread = new Runnable() 
			{
				@Override
				public void run() 
				{
					try 
					{
						OutputStream out = socket.getOutputStream();
						byte[] buf = msg.getBytes("UTF-8");
						out.write(buf);
						out.flush();
					} 
					catch (Exception e) 
					{
						try
						{
							System.out.println("Failure] " + socket.getRemoteSocketAddress() + ": "
									+ Thread.currentThread().getName());
							clients.remove(Client.this);
							socket.close();
						} 
						catch (Exception e2) {}
					}
				}
			};
			threadPool.submit(thread);
		}
	}
	public void startServer(String ipAddress, int port) {
		try
		{
			serverSocket = new ServerSocket();
			serverSocket.bind(new InetSocketAddress(ipAddress, port));
		} 
		catch (Exception e) 
		{
			if (!serverSocket.isClosed())
				stopServer();
			return;
		}
		Runnable thread = new Runnable()
		{
			@Override
			public void run() 
			{
				while (true) 
				{
					try 
					{
						Socket socket = serverSocket.accept();
						clients.add(new Client(socket));
						System.out.println("[Connected with client] " + socket.getRemoteSocketAddress() + ": "
								+ Thread.currentThread().getName());
					} 
					catch (Exception e)
					{
						if (!serverSocket.isClosed())
							stopServer();
						break;
					}
				}
			}
		};
		threadPool = Executors.newCachedThreadPool();
		threadPool.submit(thread);
	}
	public void stopServer()
	{
		try
		{
			Iterator<Client> iterator = clients.iterator();
			while (iterator.hasNext()) 
			{
				Client client = iterator.next();
				client.socket.close();
				iterator.remove();
			}
			if (serverSocket != null && !serverSocket.isClosed())
				serverSocket.close();
			if (threadPool != null && !threadPool.isShutdown())
				threadPool.shutdown();
		}
		catch (Exception e) {}
	}
	public void start(Stage primaryStage) throws Exception
	{
		BorderPane root = new BorderPane();
		root.setPadding(new Insets(5));

		TextArea txtDisplay = new TextArea();
		txtDisplay.setEditable(false);
		txtDisplay.setFont(new Font("Consolas", 14));
		root.setCenter(txtDisplay);

		Button btnStartStop = new Button("Start");
		btnStartStop.setMaxWidth(Double.MAX_VALUE);
		BorderPane.setMargin(btnStartStop, new Insets(1, 0, 0, 0));
		root.setBottom(btnStartStop);

		String ipAddress = "localhost";
		int port = 4000;

		btnStartStop.setOnAction(event -> {
			if (btnStartStop.getText().equals("Start")) {
				startServer(ipAddress, port);
				Platform.runLater(() -> {
					String str = String.format("[Server(%s:%d) started...]\n", ipAddress, port);
					txtDisplay.appendText(str);
					btnStartStop.setText("Stop");
				});
			} 
			else 
			{
				stopServer();
				Platform.runLater(() -> {
					txtDisplay.appendText("[Server stopped...]\n");
					btnStartStop.setText("Start");
				});
			}
		});
		
		Scene scene = new Scene(root, 400, 300);
		primaryStage.setTitle("Chatting Server");
		primaryStage.setOnCloseRequest(event -> stopServer());
		primaryStage.setScene(scene);
		primaryStage.show();
	}
	public class ChatClient extends Application {
		Socket socket;
		TextArea txtDisplay;

		public void startClient(String ipAddress, int port) {
			Thread thread = new Thread() 
			{
				public void run()
				{
					try 
					{
						socket = new Socket();
						socket.connect(new InetSocketAddress(ipAddress, port));
						receive();
					} 
					catch (Exception e) 
					{
						if (!socket.isClosed())stopClient();
						System.out.println("[Server connection failure!!");
						Platform.exit();
					}
				}
			};
			thread.start();
		}
		public void stopClient() 
		{
			try
			{
				if (socket != null && !socket.isClosed())
				{
					socket.close();
				}
			} 
			catch (Exception e) 
			{
			}
		}
		public void receive()
		{
			while (true)
			{
				try {
					InputStream in = socket.getInputStream();
					byte[] buf = new byte[100];
					int len = in.read(buf);
					if (len == -1)
						throw new IOException();

					String msg = new String(buf, 0, len, "UTF-8");
					Platform.runLater(() -> {txtDisplay.appendText(msg);});
				} 
				catch (IOException e) {
					stopClient(); break;
				}
			}
		}
		public void send(String msg) 
		{
			Thread thread = new Thread()
			{
				public void run() 
				{
					try 
					{
						OutputStream out = socket.getOutputStream();
						byte[] buf = msg.getBytes("UTF-8");
						out.write(buf);
						out.flush();
					} 
					catch (Exception e) 
					{
						stopClient();
					}
				}
			};
			thread.start();
		}
		public void start(Stage primaryStage) throws Exception 
		{
			BorderPane root = new BorderPane();
			root.setPadding(new Insets(5));

			HBox hbox = new HBox();
			hbox.setSpacing(5);

			TextField txtName = new TextField();
			txtName.setPrefWidth(150);
			txtName.setPromptText("Input your nickname.");

			TextField txtIP = new TextField("localhost");
			HBox.setHgrow(txtIP, Priority.ALWAYS);

			TextField txtPort = new TextField("4000");
			txtPort.setPrefWidth(80);

			hbox.getChildren().addAll(txtName, txtIP, txtPort);
			root.setTop(hbox);

			txtDisplay = new TextArea();
			txtDisplay.setEditable(false);
			root.setCenter(txtDisplay);

			BorderPane pane = new BorderPane();

			TextField txtInput = new TextField();
			txtInput.setPrefWidth(Double.MAX_VALUE);
			txtInput.setDisable(true);

			txtInput.setOnAction(event -> {
				send(txtName.getText() + "> " + txtInput.getText() + "\n");
				txtInput.setText("");
				txtInput.requestFocus();
			});
			Button btnSend = new Button("Send");
			btnSend.setDisable(true);

			btnSend.setOnAction(event -> {
				send(txtName.getText() + "> " + txtInput.getText() + "\n");
				txtInput.setText("");
				txtInput.requestFocus();
			});
			Button btnConn = new Button("Start");
			btnConn.setOnAction(event -> {
				if (btnConn.getText().equals("Start")) 
				{
					int port = 4000;
					try 
					{
						port = Integer.parseInt(txtPort.getText());
					} 
					catch (Exception e) {}
					startClient(txtIP.getText(), port);
					Platform.runLater(() -> {
						txtDisplay.appendText("Chatting Started...\n");
					});
					btnConn.setText("Stop");
					txtInput.setDisable(false);
					btnSend.setDisable(false);
					txtInput.requestFocus();
				} 
				else 
				{
					stopClient();
					Platform.runLater(() -> {
						txtDisplay.appendText("Chatting Stopped...\n");
					});
					btnConn.setText("Start");
					txtInput.setDisable(true);
					btnSend.setDisable(true);
				}
			});
			pane.setLeft(btnConn);
			pane.setCenter(txtInput);
			pane.setRight(btnSend);
			root.setBottom(pane);

			Scene scene = new Scene(root, 500, 300);
			primaryStage.setTitle("Chatting Client");
			primaryStage.setScene(scene);
			primaryStage.setOnCloseRequest(event -> stopClient());
			primaryStage.show();
			btnConn.requestFocus();
		}
	}
	public static void main(String[] args) 
	{
		launch(args);
	}
}
