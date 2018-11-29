package com.smddzcy.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

interface Base extends Serializable {
    UUID getId();
}

class User implements Base {
    private static final long serialVersionUID = 6640946451799215486L;
    private UUID id;
    private String username;
    private String password;

    public User() {
    }

    public User(String username, String password) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.password = password;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

class Incident implements Base {
    private static final long serialVersionUID = -2609379500075639388L;
    private UUID id;
    private String reportedBy; // username
    private String location;
    private String date;
    private String incident;

    public Incident() {
    }

    public Incident(String reportedBy, String location, String incident) {
        this.id = UUID.randomUUID();
        this.reportedBy = reportedBy;
        this.location = location;
        this.date = new SimpleDateFormat().format(new Date());
        this.incident = incident;
    }

    public UUID getId() {
        return id;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public String getDate() {
        return date;
    }

    public String getLocation() {
        return location;
    }

    public String getIncident() {
        return incident;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

class IncidentList {
    List<Incident> items;
}

class UserList {
    List<User> items;
}


@SuppressWarnings("unchecked")
public class DB {
    private static final String USER_DB = "./users.dat";
    private static final String INCIDENT_DB = "./incidents.dat";
    private static ReentrantLock userLock = new ReentrantLock(true);
    private static ReentrantLock incidentLock = new ReentrantLock(true);
    private static Map<Socket, Consumer<List<Incident>>> listeningSockets =
        new HashMap<>();
    private static Gson gson = new Gson();

    private DB() {
        throw new Error("Don't instantiate DB");
    }

    /**
     * Reads a data file with the given {fileName} and returns its text
     * contents.
     */
    private static String readDataFile(String fileName) {
        try {
            BufferedReader br = new BufferedReader(
                new FileReader(new File(fileName)));
            try {
                return br.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (FileNotFoundException ignored) {
            File f = new File(fileName);
            try {
                f.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "[]";
        }
    }

    /**
     * Writes {data} to the data file with the given {fileName}.
     */
    private static void writeToDataFile(String fileName, String data) {
        try {
            FileWriter fw = new FileWriter(new File(fileName));
            fw.write(data);
            fw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Type getType(String fileName) {
        if (fileName.equals(USER_DB)) {
            return new TypeToken<List<User>>() {}.getType();
        }
        return new TypeToken<List<Incident>>() {}.getType();
    }

    private static <T extends Base> List<T> get(Lock lock, String fileName) {
        lock.lock();
        List<T> objs = gson.fromJson(readDataFile(fileName), getType(fileName));
        if (objs == null) { objs = new ArrayList<>(); }
        lock.unlock();
        return objs;
    }

    private static <T extends Base> List<T> add(Lock lock, String fileName, T
        obj) {
        lock.lock();
        List<T> objs = gson.fromJson(readDataFile(fileName), getType(fileName));
        if (objs == null) { objs = new ArrayList<>(); }
        objs.add(obj);
        writeToDataFile(fileName, gson.toJson(objs));
        lock.unlock();
        return objs;
    }

    private static <T extends Base> List<T> update(Lock lock, String
        fileName, UUID id, T obj) {
        lock.lock();
        List<T> objs = gson.fromJson(readDataFile(fileName), getType(fileName));
        if (objs == null) { objs = new ArrayList<>(); }
        objs = objs.stream().filter(o -> !o.getId().equals(obj.getId()))
                   .collect(Collectors.toList());
        objs.add(obj);
        writeToDataFile(fileName, gson.toJson(objs));
        lock.unlock();
        return objs;
    }

    private static <T extends Base> List<T> remove(Lock lock, String
        fileName, UUID id) {
        lock.lock();
        List<T> objs = gson.fromJson(readDataFile(fileName), getType(fileName));
        if (objs == null) { objs = new ArrayList<>(); }
        objs = objs.stream().filter(o -> !o.getId().equals(id))
                   .collect(Collectors.toList());
        writeToDataFile(fileName, gson.toJson(objs));
        lock.unlock();
        return objs;
    }


    public static List<User> getUsers() {
        return get(userLock, USER_DB);
    }

    public static List<Incident> getIncidents() {
        return get(incidentLock, INCIDENT_DB);
    }

    public static List<User> addUser(User user) {
        return add(userLock, USER_DB, user);
    }

    public static List<User> removeUser(UUID id) {
        return remove(userLock, USER_DB, id);
    }

    public static List<User> updateUser(UUID id, User user) {
        return update(userLock, USER_DB, id, user);
    }

    private static List<Consumer<List<Incident>>> getAliveListeners() {
        return listeningSockets.entrySet()
                               .stream()
                               .filter((Map.Entry<Socket, ?> e) ->
                                           e.getKey().isConnected())
                               .map(Map.Entry::getValue)
                               .collect(Collectors.toList());
    }

    public static List<Incident> addIncident(Incident incident) {
        List<Incident> res = add(incidentLock, INCIDENT_DB, incident);
        getAliveListeners().forEach(c -> c.accept(res));
        return res;
    }

    public static List<Incident> updateIncident(UUID id, Incident incident) {
        List<Incident> res = update(incidentLock, INCIDENT_DB, id, incident);
        getAliveListeners().forEach(c -> c.accept(res));
        return res;
    }

    public static List<Incident> removeIncident(UUID id) {
        List<Incident> res = remove(incidentLock, INCIDENT_DB, id);
        getAliveListeners().forEach(c -> c.accept(res));
        return res;
    }

    public static void onIncidentsChange(Socket socket, Consumer<List<Incident>> consumer) {
        listeningSockets.put(socket, consumer);
    }
}
