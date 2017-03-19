package io.loyloy.fe;

import io.loyloy.fe.bungee.Synchronization;
import io.loyloy.fe.database.Account;
import io.loyloy.fe.database.Database;
import io.loyloy.fe.database.databases.MySQLDB;
import io.loyloy.fe.database.databases.SQLiteDB;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.loyloy.fe.listeners.FePlayerListener;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class Fe extends JavaPlugin
{
    private final Set<Database> databases;
    private API api;
    private Database database;
    private Synchronization synchronization;
    
    public final String incomingChannel = "BungeeCord";
    public final String outgoingChannel = "BungeeCord"; 

    public Fe()
    {
        databases = new HashSet<Database>();
    }

    public void onEnable()
    {
        getDataFolder().mkdirs();

        Phrase.init( this );

        databases.add( new MySQLDB( this ) );
        databases.add( new SQLiteDB( this ) );

        for( Database database : databases )
        {
            String name = database.getConfigName();

            ConfigurationSection section = getConfig().getConfigurationSection( name );

            if( section == null )
            {
                section = getConfig().createSection( name );
            }

            database.getConfigDefaults( section );

            if( section.getKeys( false ).isEmpty() )
            {
                getConfig().set( name, null );
            }
        }

        getConfig().options().copyDefaults( true );

        getConfig().options().header( "Fe Config - loyloy.io\n" +
                "holdings - The amount of money that players will start out with\n" +
                "prefix - The message prefix\n" +
                "currency - The single and multiple names for the currency\n" +
                "type - The type of database used (sqlite, mysql, or mongo)" );

        saveConfig();

        api = new API( this );

        synchronization = new Synchronization( this );
        
        if( !setupDatabase() )
        {
            return;
        }

        getCommand( "fe" ).setExecutor( new FeCommand( this ) );

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents( new FePlayerListener( this ), this );

        setupVault();

        reloadConfig();

        // Auto Clean On Startup
        if( api.isAutoClean() )
        {
            api.clean();
            log( Phrase.ACCOUNT_CLEANED );
        }
    }

    public void log( String message )
    {
        getLogger().info( message );
    }

    public void onDisable()
    {
        getServer().getScheduler().cancelTasks( this );

        getFeDatabase().close();
    }

    public void log( Phrase phrase, String... args )
    {
        log( phrase.parse( args ) );
    }

    public Database getFeDatabase()
    {
        return database;
    }

    public boolean addDatabase( Database database )
    {
        return databases.add( database );
    }

    public Set<Database> getDatabases()
    {
        return new HashSet<Database>( databases );
    }

    public API getAPI()
    {
        return api;
    }

    public Synchronization getSynchronization()
    {
        return synchronization;
    }

    private boolean setupDatabase()
    {
        String type = getConfig().getString( "type" );

        database = null;

        for( Database database : databases )
        {
            if( type.equalsIgnoreCase( database.getConfigName() ) )
            {
                this.database = database;

                break;
            }
        }

        if( database == null )
        {
            log( Phrase.DATABASE_TYPE_DOES_NOT_EXIST );

            return false;
        }

        if( !database.init() )
        {
            log( Phrase.DATABASE_FAILURE_DISABLE );

            setEnabled( false );

            return false;
        }

        return true;
    }

    private void setupPhrases()
    {
        File phrasesFile = new File( getDataFolder(), "phrases.yml" );

        for( Phrase phrase : Phrase.values() )
        {
            phrase.reset();
        }

        if( !phrasesFile.exists() )
        {
            return;
        }

        YamlConfiguration phrasesConfig = YamlConfiguration.loadConfiguration( phrasesFile );

        for( Phrase phrase : Phrase.values() )
        {
            String phraseConfigName = phrase.getConfigName();

            String phraseMessage = phrasesConfig.getString( phraseConfigName );

            if( phraseMessage == null )
            {
                phraseMessage = phrase.parse();
            }

            phrase.setMessage( phraseMessage );
        }
    }

    public void reloadConfig()
    {
        super.reloadConfig();

        String oldCurrencySingle = getConfig().getString( "currency.single" );

        String oldCurrencyMultiple = getConfig().getString( "currency.multiple" );

        if( oldCurrencySingle != null )
        {
            getConfig().set( "currency.major.single", oldCurrencySingle );

            getConfig().set( "currency.single", null );
        }

        if( oldCurrencyMultiple != null )
        {
            getConfig().set( "currency.major.multiple", oldCurrencyMultiple );

            getConfig().set( "currency.multiple", null );
        }

        if( !getConfig().isSet( "autoclean" ) )
        {
            getConfig().set( "autoclean", true );
        }

        // Temporarily remove cache and updates.
        if( getConfig().isSet( "cacheaccounts" ) )
        {
            getConfig().set( "cacheaccounts", null );
        }
        if( getConfig().getBoolean( "updatecheck" ) )
        {
            getConfig().set( "updatecheck", null );
        }

        setupPhrases();

        saveConfig();
    }

    @SuppressWarnings( "deprecation" )
    public Account getShortenedAccount( String name )
    {
        Account account = getAPI().getAccount( name, null );

        if( account == null )
        {
            Player player = getServer().getPlayer( name );

            if( player != null )
            {
                account = getAPI().getAccount( player.getName(), null );
            }
        }

        return account;
    }

    public String getMessagePrefix()
    {
        String third = Phrase.TERTIARY_COLOR.parse();

        return third + "[" + Phrase.PRIMARY_COLOR.parse() + "$1" + third + "] " + Phrase.SECONDARY_COLOR.parse();
    }

    public String getEqualMessage( String inBetween, int length )
    {
        return getEqualMessage( inBetween, length, length );
    }

    public String getEqualMessage( String inBetween, int length, int length2 )
    {
        String equals = getEndEqualMessage( length );

        String end = getEndEqualMessage( length2 );

        String third = Phrase.TERTIARY_COLOR.parse();

        return equals + third + "[" + Phrase.PRIMARY_COLOR.parse() + inBetween + third + "]" + end;
    }

    public String getEndEqualMessage( int length )
    {
        String message = Phrase.SECONDARY_COLOR.parse() + "";

        for( int i = 0; i < length; i++ )
        {
            message += "=";
        }

        return message;
    }

    private void setupVault()
    {
        Plugin vault = getServer().getPluginManager().getPlugin( "Vault" );

        if( vault == null )
        {
            return;
        }

        getServer().getServicesManager().register( Economy.class, new VaultHandler( this ), this, ServicePriority.Highest );
    }
}