package org.example;

import org.json.JSONObject;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;




class ServerSomthing extends Thread {
    private Socket socket; // сокет, через который сервер общается с клиентом,
    // кроме него - клиент и сервер никак не связаны
    private BufferedReader in; // поток чтения из сокета
    private BufferedWriter out; // поток завписи в сокет
    private Operations_List oper;
    PublicKey publicKey = null;
    PublicKey publicKey_client = null;
    PrivateKey privateKey = null;




    public ServerSomthing(Socket socket) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        this.socket = socket;

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        System.out.println("установлено новое соединение");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        KeyPair pair = generator.generateKeyPair();

        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
        this.read_key();
        this.send_key();

        start(); // вызываем run()
    }


    @Override
    public void run() {
        String input_string;

        try {
            while (true) {
                input_string = in.readLine();

                Cipher decryptCipher = Cipher.getInstance("RSA");
                decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

                byte[] decryptedMessageBytes = decryptCipher.doFinal(Base64.getDecoder().decode(input_string));
                String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

                JSONObject json = new JSONObject(decryptedMessage);

                if(json.getString("OPERATION").equals("-1")) {
                    this.downService(); // харакири
                    break;
                }

                this.oper = new Operations_List(Server.pg_adr,Server.pg_password, json);
                var out_result = this.oper.processing(json.getString("OPERATION"));

                Server.logger.log(Level.INFO, String.format("расшифрованная строка - %s\nрезультат работы - %s\n", decryptedMessage, out_result));
                this.send(out_result);

            }
        } catch (NullPointerException ignored)
        {
            Server.logger_error.log(Level.INFO, "ошибка в функции run - NullPointerException - класс server");
        }
        catch (Exception e) {
            Server.logger_error.log(Level.INFO, "ошибка в функции run - Exception - класс server");
            throw new RuntimeException(e);
        }
    }


    private void read_key() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        String input_string = in.readLine();

        byte[] data = Base64.getDecoder().decode(input_string.getBytes());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        this.publicKey_client = fact.generatePublic(spec);
    }

    private void send_key() {
        try {
            out.write(Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n");
            out.flush();
        } catch (IOException ignored)
        {
            Server.logger_error.log(Level.INFO, "ошибка в функции send_key - IOexception - класс server");
        }

    }

    private void send(String msg) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");//строгое соблюдение
            cipher.init(Cipher.ENCRYPT_MODE, publicKey_client);
            out.write(Base64.getEncoder().encodeToString(cipher.doFinal(msg.getBytes())) + "\n");
            out.flush();
        }
        catch (IOException ignored)
        {
            Server.logger_error.log(Level.INFO, "ошибка в функции send - IOexception - класс server");
        }
        catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e)
        {
            Server.logger_error.log(Level.INFO, "ошибка в функции send - NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException - класс server");
            throw new RuntimeException(e);
        }

    }

    /**
     * закрытие сервера
     * прерывание себя как нити и удаление из списка нитей
     */
    private void downService() {
        try {
            if(!socket.isClosed()) {
                socket.close();
                in.close();
                out.close();
                for (ServerSomthing vr : Server.serverList) {
                    if(vr.equals(this)) vr.interrupt();
                    Server.serverList.remove(this);
                }
                System.out.println("соединение разорвано \n");
            }
        } catch (IOException ignored)
        {
            Server.logger_error.log(Level.INFO, "ошибка в функции downService - IOexception - класс server");
        }
    }
}



public class Server {
    private static final int PORT = 8080;
    public static String pg_adr;
    public static String pg_password;
    public static LinkedList<ServerSomthing> serverList = new LinkedList<>(); // список всех нитей - экземпляров сервера, слушающих каждый своего клиента
    public static Logger logger;
    public static Logger logger_error;

    public static void main(String[] args) throws IOException {

//        BufferedReader cons = new BufferedReader(new InputStreamReader(System.in));
//        System.out.println("введите адрес БД");
//        pg_adr = cons.readLine();
//        System.out.println("введите пароль БД");
//        pg_password = cons.readLine();

        Properties prop = new Properties();
        String fileName = "./server.config";
        try (FileInputStream fis = new FileInputStream(fileName)) {
            prop.load(fis);
        } catch (IOException ex) {}


        String filePattern = "./log/log.log";
        int limit = 1000 * 1000; // 1 Mb
        int numLogFiles = 3;

        logger = Logger.getLogger("MyLog");
        FileHandler fh;
        try {
            fh = new FileHandler(filePattern, limit, numLogFiles,true);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }


        String filePattern_error = "./log_error/log.log";
        logger_error = Logger.getLogger("MyLog");
        FileHandler fh_error;
        try {
            fh_error = new FileHandler(filePattern_error, limit, numLogFiles,true);
            logger.addHandler(fh_error);
            SimpleFormatter formatter = new SimpleFormatter();
            fh_error.setFormatter(formatter);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }


        System.out.println(prop.getProperty("server.address"));
        pg_adr = prop.getProperty("server.address");
        System.out.println(prop.getProperty("server.password"));
        pg_password = prop.getProperty("server.password");


        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server Started");
        try {
            while (true) {
                // Блокируется до возникновения нового соединения:
                Socket socket = server.accept();
                try {
                    serverList.add(new ServerSomthing(socket)); // добавить новое соединенние в список
                } catch (IOException e)
                {
                    // Если завершится неудачей, закрывается сокет,
                    // в противном случае, нить закроет его:
                    logger_error.log(Level.INFO, "ошибка в функции main - IOexception - класс server");
                    socket.close();
                }
                catch (NoSuchAlgorithmException | InvalidKeySpecException e)
                {
                    logger_error.log(Level.INFO, "ошибка в функции main - NoSuchAlgorithmException | InvalidKeySpecException - класс server");
                    throw new RuntimeException(e);
                }
            }
        } finally {
            server.close();
        }
    }
}