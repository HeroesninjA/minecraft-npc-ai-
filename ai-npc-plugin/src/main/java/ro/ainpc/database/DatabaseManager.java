package ro.ainpc.database;

import ro.ainpc.AINPCPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final AINPCPlugin plugin;
    private Connection connection;

    public DatabaseManager(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            // Creaza folderul pentru baza de date daca nu exista
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            String filename = plugin.getConfig().getString("database.filename", "ainpc_data.db");
            File dbFile = new File(plugin.getDataFolder(), filename);
            
            // Conectare la baza de date SQLite
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            
            // Creaza tabelele
            createTables();
            
            plugin.getLogger().info("Baza de date initializata cu succes!");
            return true;
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Driver SQLite negasit!", e);
            return false;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Eroare la conectarea la baza de date!", e);
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Tabel NPC-uri
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npcs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    display_name TEXT,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL DEFAULT 0,
                    pitch REAL DEFAULT 0,
                    skin_texture TEXT,
                    skin_signature TEXT,
                    backstory TEXT,
                    occupation TEXT,
                    age INTEGER DEFAULT 30,
                    gender TEXT DEFAULT 'male',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            // Tabel personalitate (Big Five traits)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npc_personality (
                    npc_id INTEGER PRIMARY KEY,
                    openness REAL DEFAULT 0.5,
                    conscientiousness REAL DEFAULT 0.5,
                    extraversion REAL DEFAULT 0.5,
                    agreeableness REAL DEFAULT 0.5,
                    neuroticism REAL DEFAULT 0.5,
                    FOREIGN KEY (npc_id) REFERENCES npcs(id) ON DELETE CASCADE
                )
            """);
            
            // Tabel emotii curente
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npc_emotions (
                    npc_id INTEGER PRIMARY KEY,
                    happiness REAL DEFAULT 0.5,
                    sadness REAL DEFAULT 0.0,
                    anger REAL DEFAULT 0.0,
                    fear REAL DEFAULT 0.0,
                    surprise REAL DEFAULT 0.0,
                    disgust REAL DEFAULT 0.0,
                    trust REAL DEFAULT 0.5,
                    anticipation REAL DEFAULT 0.3,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (npc_id) REFERENCES npcs(id) ON DELETE CASCADE
                )
            """);
            
            // Tabel amintiri
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npc_memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    npc_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    memory_type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    emotional_impact REAL DEFAULT 0.0,
                    importance INTEGER DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP,
                    FOREIGN KEY (npc_id) REFERENCES npcs(id) ON DELETE CASCADE
                )
            """);
            
            // Index pentru cautare rapida amintiri
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_memories_npc_player 
                ON npc_memories(npc_id, player_uuid)
            """);
            
            // Tabel relatii cu jucatorii
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npc_relationships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    npc_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    affection REAL DEFAULT 0.0,
                    trust REAL DEFAULT 0.0,
                    respect REAL DEFAULT 0.0,
                    familiarity REAL DEFAULT 0.0,
                    interaction_count INTEGER DEFAULT 0,
                    last_interaction TIMESTAMP,
                    relationship_type TEXT DEFAULT 'stranger',
                    FOREIGN KEY (npc_id) REFERENCES npcs(id) ON DELETE CASCADE,
                    UNIQUE(npc_id, player_uuid)
                )
            """);
            
            // Tabel familie
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npc_family (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    npc_id INTEGER NOT NULL,
                    related_npc_id INTEGER,
                    related_name TEXT NOT NULL,
                    relation_type TEXT NOT NULL,
                    is_alive INTEGER DEFAULT 1,
                    backstory TEXT,
                    FOREIGN KEY (npc_id) REFERENCES npcs(id) ON DELETE CASCADE,
                    FOREIGN KEY (related_npc_id) REFERENCES npcs(id) ON DELETE SET NULL
                )
            """);
            
            // Tabel dialog history
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dialog_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    npc_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_message TEXT NOT NULL,
                    npc_response TEXT NOT NULL,
                    emotion_state TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (npc_id) REFERENCES npcs(id) ON DELETE CASCADE
                )
            """);
            
            // Index pentru dialog history
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_dialog_npc_player 
                ON dialog_history(npc_id, player_uuid, created_at DESC)
            """);
            
            plugin.debug("Toate tabelele au fost create/verificate.");
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Eroare la verificarea conexiunii!", e);
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Conexiunea la baza de date a fost inchisa.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Eroare la inchiderea conexiunii!", e);
        }
    }

    // Metode helper pentru operatii comune
    
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return getConnection().prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return getConnection().prepareStatement(sql, autoGeneratedKeys);
    }

    public void executeUpdate(String sql) throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
}
