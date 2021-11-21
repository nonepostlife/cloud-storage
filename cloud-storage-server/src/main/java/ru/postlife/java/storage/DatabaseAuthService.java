package ru.postlife.java.storage;

import java.sql.*;


import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.*;
import ru.postlife.java.model.AuthModel;

@Slf4j
public class DatabaseAuthService implements AuthService {
    private String jdbcURL = "jdbc:mysql://localhost:3306/netty_storage?serverTimezone=UTC";
    private String connect_username = "root";
    private String connect_password = "root";
    private Connection connection;
    private Statement statement;

    @Override
    public void start() {
        try {
            connection = DriverManager.getConnection(jdbcURL, connect_username, connect_password);
            statement = connection.createStatement();
            log.info("Database auth service is start");
        } catch (SQLException throwables) {
            log.error("Database auth service was not started - " + throwables.getMessage());
            throwables.printStackTrace();
        }
    }

    @Override
    public void stop() {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            log.error("Database auth service has not been stopped (statement) - " + e.getMessage());
            e.printStackTrace();
        }
        try {
            if (connection != null) {
                connection.close();
                log.info("Database auth service is stop");
            }
        } catch (SQLException e) {
            log.error("Database auth service has not been stopped (connection) - " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public AuthModel checkUserByLoginPass(AuthModel authModel) {
        if (connection == null) {
            authModel.setAuth(false);
            authModel.setResponse("Database is down");
            return authModel;
        }
        try (PreparedStatement ps = connection.prepareStatement("select user_password, user_firstname, user_lastname from user where user_login = ?")) {
            ps.setString(1, authModel.getLogin());
            ps.execute();

            ResultSet rs = ps.getResultSet();
            String stored_hash;
            String firstname;
            String lastname;

            if (rs.next()) {
                log.debug("user find");
                stored_hash = rs.getString("user_password");
                firstname = rs.getString("user_firstname");
                lastname = rs.getString("user_lastname");

                if (checkPassword(authModel.getPassword(), stored_hash)) {
                    authModel.setResponse("Authorization is successful!");
                    authModel.setFirstname(firstname);
                    authModel.setLastname(lastname);
                    authModel.setAuth(true);
                }
            } else {
                log.debug("user dont find");
                authModel.setResponse(String.format("User %s dont found", authModel.getLogin()));
                authModel.setAuth(false);
            }
        } catch (SQLException e) {
            log.error("e", e);
        }
        return authModel;
    }

    @Override
    public String registerNewUser(String login, String password) {
        try (PreparedStatement ps = connection.prepareStatement("insert into user (user_login, user_password) values (?,?)")) {
            ps.setString(1, login);
            ps.setString(2, hashPassword(password));
            ps.execute();
            return "/registerok Registration successful";
        } catch (SQLException throwables) {
            return "/registerfail " + throwables.getMessage();
        }
    }

    // Define the BCrypt workload to use when generating password hashes. 10-31 is a valid value.
    private static int workload = 12;

    /**
     * This method can be used to generate a string representing an account password
     * suitable for storing in a database. It will be an OpenBSD-style crypt(3) formatted
     * hash string of length=60
     * The bcrypt workload is specified in the above static variable, a value from 10 to 31.
     * A workload of 12 is a very reasonable safe default as of 2013.
     * This automatically handles secure 128-bit salt generation and storage within the hash.
     *
     * @param password_plaintext The account's plaintext password as provided during account creation,
     *                           or when changing an account's password.
     * @return String - a string of length 60 that is the bcrypt hashed password in crypt(3) format.
     */
    public String hashPassword(String password_plaintext) {
        String salt = BCrypt.gensalt(workload);

        return BCrypt.hashpw(password_plaintext, salt);
    }

    /**
     * This method can be used to verify a computed hash from a plaintext (e.g. during a login
     * request) with that of a stored hash from a database. The password hash from the database
     * must be passed as the second variable.
     *
     * @param password_plaintext The account's plaintext password, as provided during a login request
     * @param stored_hash        The account's stored password hash, retrieved from the authorization database
     * @return boolean - true if the password matches the password of the stored hash, false otherwise
     */
    public boolean checkPassword(String password_plaintext, String stored_hash) {
        boolean password_verified = false;

        if (null == stored_hash || !stored_hash.startsWith("$2a$"))
            throw new java.lang.IllegalArgumentException("Invalid hash provided for comparison");

        password_verified = BCrypt.checkpw(password_plaintext, stored_hash);

        return (password_verified);
    }
}
