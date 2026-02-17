package org.ldap.apacheds;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class LdapServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapServer.class);
    private static final File WORKDIR = new File("/tmp/data-ldapserver");
    private static final File FILE_USED_LDIF = new File(WORKDIR, "file-used.ldif");
    private static final int DEFAULT_LDAP_PORT = 10389;
    private static final String ENV_LDAP_PORT = "LDAP_PORT";

    public static void main(String[] args) {
        try {
            final boolean isFirstRun = !WORKDIR.exists();
            // on first run, save file.ldif
            if (isFirstRun) {
                if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
                    throw new RuntimeException("ERROR: at first run it is mandatory to specify a file.ldif.");
                }
                final File originalLdif = new File(args[0]);
                if (!originalLdif.exists() || !originalLdif.isFile()) {
                    throw new RuntimeException("ERROR: file.ldif not found in: " + args[0]);
                }
                if (!WORKDIR.mkdirs()) {
                    throw new RuntimeException("ERROR: can not create WORKDIR: " + WORKDIR.getAbsolutePath());
                }
                Files.copy(originalLdif.toPath(), FILE_USED_LDIF.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("file.ldif saved in: {}", FILE_USED_LDIF.getAbsolutePath());
            } else {
                if (!FILE_USED_LDIF.exists()) {
                    throw new RuntimeException("ERROR: can not find: " + FILE_USED_LDIF.getAbsolutePath());
                }
            }

            final DefaultDirectoryServiceFactory factory = new DefaultDirectoryServiceFactory();
            factory.init("ldapserver");
            final DirectoryService service = factory.getDirectoryService();
            service.getChangeLog().setEnabled(false);
            service.setInstanceLayout(new InstanceLayout(WORKDIR));
            final List<LdifEntry> entries = new ArrayList<>();
            try (LdifReader reader = new LdifReader(FILE_USED_LDIF)) {
                reader.forEach(entries::add);
            }
            final Dn rootDn = entries.stream()
                    .map(LdifEntry::getDn)
                    .min(Comparator.comparingInt(Dn::size))
                    .orElseThrow(() -> new RuntimeException("ERROR: LDIF parse error."));
            final String partitionId = rootDn.getRdn().getValue();
            final JdbmPartition partition = new JdbmPartition(service.getSchemaManager(), service.getDnFactory());
            partition.setId(partitionId);
            partition.setSuffixDn(rootDn);
            partition.setPartitionPath(new File(service.getInstanceLayout().getPartitionsDirectory(), partitionId).toURI());
            service.addPartition(partition);
            service.startup();
            if (isFirstRun) {
                entries.forEach(e -> {
                    try {
                        service.getAdminSession().add(new DefaultEntry(service.getSchemaManager(), e.getEntry()));
                    } catch (Exception ex) {
                        LOGGER.error("ERROR: can not import entry {}: {}", e.getDn(), ex.getMessage());
                    }
                });
            }
            org.apache.directory.server.ldap.LdapServer ldapServer = new org.apache.directory.server.ldap.LdapServer();
            ldapServer.setDirectoryService(service);
            final Integer ldapPort = Optional.ofNullable(System.getenv(ENV_LDAP_PORT))
                    .map(Integer::parseInt).orElse(DEFAULT_LDAP_PORT);
            ldapServer.setTransports(new TcpTransport(ldapPort));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    LOGGER.info("Server LDAP shutting down...");
                    ldapServer.stop();
                    service.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
            ldapServer.start();
            LOGGER.info("Server LDAP started on localhost: {} (DN: {})", ldapPort, rootDn);
        } catch (final Throwable ex) {
            LOGGER.error("ERROR: fatal error during startup");
            ex.printStackTrace();
            System.exit(1);
        }
    }
}