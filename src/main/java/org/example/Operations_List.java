package org.example;

import org.json.JSONObject;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Operations_List {

    Map<String, Callable<String>> commands;
    private String url;
    private String user;
    private String password;

    public Operations_List(String adr, String pass, JSONObject json) {

        this.url = String.format("jdbc:postgresql://%s:5432/online_bank", adr);
        this.user = "postgres";
        this.password = String.format("%s", pass);

        this.commands = new HashMap<String, Callable<String>>();
        //TODO:здесь список операций
        this.commands.put("1", () -> login(json));
        this.commands.put("2", () -> phone_check(json));
        this.commands.put("3", () -> registration_user(json));


    }

    public String processing(String cmd) throws Exception {
        return commands.get(cmd).call();
    }

    public String login(JSONObject json) throws IOException, SQLException {
        System.out.println("запрос на вход");
        JSONObject json_out = new JSONObject();

        String query = "select count(id) from bank_test.login_data where login_phone like '"+ json.getString("LOGIN")
                +"' and password like '"+ json.getString("PASSWORD") +"';";


        try (Connection con = DriverManager.getConnection(url, user, password);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(query))
        {

            if (rs.next()) {
                json_out.put("EXIST", rs.getString(1));
            }

            return json_out.toString();

        } catch (SQLException ex) {
            System.out.println("error");
            Logger lgr = Logger.getLogger(ServerSomthing.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
        }
        json_out.put("EXIST", "0");
        return json_out.toString();
    }

    public String phone_check(JSONObject json) throws IOException, SQLException {
        System.out.println("запрос проверки телфона");
        JSONObject json_out = new JSONObject();

        String query = String.format("select count(login_phone) from bank_test.login_data where login_phone ilike '%s' ", json.getString("PHONE"));

        try (Connection con = DriverManager.getConnection(url, user, password);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(query))
        {

            if (rs.next()) {
                json_out.put("EXIST", rs.getString(1));
            }

            return json_out.toString();

        } catch (SQLException ex) {
            System.out.println("error");
            Logger lgr = Logger.getLogger(ServerSomthing.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
        }


        json_out.put("EXIST", "0");
        return json_out.toString();
    }

    public String registration_user(JSONObject json)
    {
        return null;
    }
}
