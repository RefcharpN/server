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

    public String login(JSONObject json) {
        System.out.println("запрос на вход");
        JSONObject json_out = new JSONObject();

        String query = String.format("select * from bank_test.login('%s','%s')", json.getString("LOGIN"), json.getString("PASSWORD"));

        try (Connection con = DriverManager.getConnection(url, user, password);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(query))
        {

            if (rs.next()) {
                json_out.put("EXIST", rs.getString(1));
            }

            return json_out.toString();

        } catch (SQLException ex) {
            Server.logger_error.log(Level.INFO, "ошибка в функции login - SQLException - класс Operation_list" + ex);
        }
        json_out.put("EXIST", "0");
        return json_out.toString();
    }

    public String phone_check(JSONObject json) {
        System.out.println("запрос проверки телефона");
        JSONObject json_out = new JSONObject();

        String query = String.format("select * from bank_test.phone_check('%s')", json.getString("PHONE"));

        try (Connection con = DriverManager.getConnection(url, user, password);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(query))
        {

            if (rs.next()) {
                json_out.put("EXIST", rs.getString(1));
            }

            return json_out.toString();

        } catch (SQLException ex)
        {
            Server.logger_error.log(Level.INFO, "ошибка в функции phone_check - SQLException - класс Operation_list");
        }


        json_out.put("EXIST", "0");
        return json_out.toString();
    }

    public String registration_user(JSONObject json)
    {
        System.out.println("регистрация клиента");
        JSONObject json_out = new JSONObject();

        String query = String.format("select * from bank_test.client_register('%s', '%s', '%s', '%s', '%s', %s, %s, '%s', '%s')",
                json.getString("phone"),
                json.getString("password"),json.getString("lname"),
                json.getString("fname"),json.getString("mname"),
                json.getString("series"),json.getString("number"),
                json.getString("date"),json.getString("department"));


        try (Connection con = DriverManager.getConnection(url, user, password);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(query))//TODO проверка ошибок выполнения
        {
//
//            if (rs.next()) {
//                json_out.put("EXIST", rs.getString(1));
//            }
//            return json_out.toString();

        } catch (SQLException ex) {
            Server.logger_error.log(Level.INFO, "ошибка в функции registration_user - SQLException - класс Operation_list");
            json_out.put("register_status", "1");
            return json_out.toString();
        }

        json_out.put("register_status", "0");
        return json_out.toString();
    }
}
