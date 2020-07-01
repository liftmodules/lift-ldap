package net.liftweb.ldap;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.FileUtils;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;


/**
 * Based on a sample from
 * http://svn.apache.org/repos/asf/directory/sandbox/kayyagari/embedded-sample-trunk/src/main/java/org/apache/directory/seserver/EmbeddedADSVerTrunk.java
 */
public class EmbeddedADS
{
    private final String rootDn;

    private DirectoryService service;
    private LdapServer server;
    private File workDir;


    /**
     * Add a new partition to the server
     *
     * @param partitionId The partition Id
     * @param partitionDn The partition DN
     * @param dnFactory the DN factory
     * @return The newly added partition
     * @throws Exception If the partition can't be added
     */
    private Partition addPartition( String partitionId, String partitionDn, DnFactory dnFactory ) throws Exception
    {
        // Create a new partition with the given partition id
        JdbmPartition partition = new JdbmPartition(service.getSchemaManager(), dnFactory);
        partition.setId( partitionId );
        partition.setPartitionPath( new File( service.getInstanceLayout().getPartitionsDirectory(), partitionId ).toURI() );
        partition.setSuffixDn( new Dn( partitionDn ) );
        service.addPartition( partition );

        return partition;
    }


    /**
     * Add a new set of index on the given attributes
     *
     * @param partition The partition on which we want to add index
     * @param attrs The list of attributes to index
     */
    private void addIndex( Partition partition, String... attrs )
    {
        // Index some attributes on the apache partition
        Set indexedAttributes = new HashSet();

        for ( String attribute : attrs )
        {
            indexedAttributes.add( new JdbmIndex( attribute, false ) );
        }

        ( ( JdbmPartition ) partition ).setIndexedAttributes( indexedAttributes );
    }


    /**
     * initialize the schema manager and add the schema partition to diectory service
     *
     * @throws Exception if the schema LDIF files are not found on the classpath
     */
    private void initSchemaPartition() throws Exception
    {
        InstanceLayout instanceLayout = service.getInstanceLayout();

        File schemaPartitionDirectory = new File( instanceLayout.getPartitionsDirectory(), "schema" );

        // Extract the schema on disk (a brand new one) and load the registries
        if ( schemaPartitionDirectory.exists() )
        {
            System.out.println( "schema partition already exists, skipping schema extraction" );
        }
        else
        {
            SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( instanceLayout.getPartitionsDirectory() );
            extractor.extractOrCopy();
        }

        SchemaLoader loader = new LdifSchemaLoader( schemaPartitionDirectory );
        SchemaManager schemaManager = new DefaultSchemaManager( loader );

        // We have to load the schema now, otherwise we won't be able
        // to initialize the Partitions, as we won't be able to parse
        // and normalize their suffix Dn
        schemaManager.loadAllEnabled();

        List<Throwable> errors = schemaManager.getErrors();

        if ( errors.size() != 0 )
        {
            throw new Exception( I18n.err( I18n.ERR_317, Exceptions.printErrors( errors ) ) );
        }

        service.setSchemaManager( schemaManager );

        // Init the LdifPartition with schema
        LdifPartition schemaLdifPartition = new LdifPartition( schemaManager, service.getDnFactory() );
        schemaLdifPartition.setPartitionPath( schemaPartitionDirectory.toURI() );

        // The schema partition
        SchemaPartition schemaPartition = new SchemaPartition( schemaManager );
        schemaPartition.setWrappedPartition( schemaLdifPartition );
        service.setSchemaPartition( schemaPartition );
    }


    /**
     * Initialize the server. It creates the partition, adds the index, and
     * injects the context entries for the created partitions.
     *
     * @param workDir the directory to be used for storing the data
     * @throws Exception if there were some problems while initializing the system
     */
    private void initDirectoryService( File workDir ) throws Exception
    {
        // Initialize the LDAP service
        service = new DefaultDirectoryService();
        service.setInstanceLayout( new InstanceLayout( workDir ) );

        // first load the schema
        initSchemaPartition();

        // then the system partition
        // this is a MANDATORY partition
        // DO NOT add this via addPartition() method, trunk code complains about duplicate partition
        // while initializing
        JdbmPartition systemPartition = new JdbmPartition(service.getSchemaManager(), service.getDnFactory());
        systemPartition.setId( "system" );
        systemPartition.setPartitionPath( new File( service.getInstanceLayout().getPartitionsDirectory(), systemPartition.getId() ).toURI() );
        systemPartition.setSuffixDn( new Dn( ServerDNConstants.SYSTEM_DN ) );

        // mandatory to call this method to set the system partition
        // Note: this system partition might be removed from trunk
        service.setSystemPartition( systemPartition );

        // Disable the ChangeLog system
        service.getChangeLog().setEnabled(false);
        service.setDenormalizeOpAttrsEnabled(true);

        // Enable anonymous search request
        service.setAllowAnonymousAccess(true);

        // Now we can create as many partitions as we need
        // Create some new partitions named 'foo', 'bar' and 'apache'.
        Partition liftPartition = addPartition( "lift-ldap", rootDn, service.getDnFactory() );

        // Index some attributes on the apache partition
        addIndex( liftPartition, "objectClass", "ou", "uid", "sn" );

        // And start the service
        service.startup();

        // Inject the context entry for ROOT_DN
        Entry rootEntry = service.newEntry( new Dn( rootDn ) );
        rootEntry.add( "objectClass", "top", "domain", "extensibleObject" );
        rootEntry.add( "dc", "liftweb" );
        service.getAdminSession().add( rootEntry );


        Dn dn = new Dn( "cn=TestUser,"+rootDn);
        Entry entry = service.newEntry( dn );
        entry.add("objectClass", "person", "organizationalPerson");
        entry.add("cn", "TestUser");
        entry.add("sn", "User");
        entry.add("userPassword", "letmein");
        service.getAdminSession().add( entry );

        // We are all done !
    }


    public EmbeddedADS(String rootDn) {
        this.rootDn = rootDn;
    }


    public void startServer(int serverPort) throws Exception
    {
        server = new LdapServer();
        server.setTransports( new TcpTransport( serverPort ) );
        server.setDirectoryService( service );
        server.start();
    }


    public void stopServer() throws Exception
    {
        server.stop();
        service.shutdown();
        if (workDir != null) {
            FileUtils.deleteDirectory(workDir);
        }
    }


    public void initServer(int serverPort) throws Exception
    {
        workDir = new File( System.getProperty( "java.io.tmpdir" ) + "/lift-ldap-server-work" );
        if (workDir.exists()) {
            FileUtils.deleteDirectory(workDir);
        }
        workDir.mkdirs();

        initDirectoryService(workDir);

        startServer(serverPort);
    }

}
