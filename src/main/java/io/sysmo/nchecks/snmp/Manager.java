/*
 * Copyright (c) 2015-2016 Sebastien Serre <ssbx@sysmo.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sysmo.nchecks.snmp;

import io.sysmo.nchecks.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.snmp4j.AbstractTarget;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivAES192;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.security.UsmUserEntry;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.transport.UdpTransportMapping;
import org.snmp4j.security.nonstandard.PrivAES192With3DESKeyExtension;
import org.snmp4j.security.nonstandard.PrivAES256With3DESKeyExtension;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableUtils;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

// TODO handle snmp v3 usm user modification;
public class Manager {

    private Snmp snmp4jSession = null;
    private final TableUtils getNextTableUtils;
    private final TableUtils getBulkTableUtils;
    private final TableUtils getTableUtils;
    private Map<String, AbstractTarget> agents;
    private Logger logger = LoggerFactory.getLogger(Manager.class);
    private static Manager instance = null;

    public static void start(final String etcDir) throws Exception {
        Manager.instance = new Manager(etcDir);
    }

    public static void stop() {
        if (Manager.instance != null) {
            Manager.instance.logger.info("stop");
            if (Manager.instance.snmp4jSession != null) {
                try {
                    Manager.instance.snmp4jSession.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private Manager(final String etcDir) throws Exception {
        try {
            String eidFile = FileSystems
                    .getDefault()
                    .getPath(etcDir, "engine.id")
                    .toString();

            byte[] engineId = Manager.getEngineId(eidFile);
            UdpTransportMapping transport = new DefaultUdpTransportMapping();
            this.snmp4jSession = new Snmp(transport);
            USM usm = new USM(SecurityProtocols.getInstance(),
                    new OctetString(engineId), 0);
            SecurityModels.getInstance().addSecurityModel(usm);
            this.agents = new HashMap<>();
            transport.listen();
        } catch (Exception e) {
            this.logger.error("SNMP init fail: " + e.getMessage(), e);
            throw e;
        }

        this.getNextTableUtils = new TableUtils(this.snmp4jSession,
                new DefaultPDUFactory(PDU.GETNEXT));
        this.getBulkTableUtils = new TableUtils(this.snmp4jSession,
                new DefaultPDUFactory(PDU.GETBULK));
        this.getTableUtils = new TableUtils(this.snmp4jSession,
                new DefaultPDUFactory(PDU.GET));

    }

    public static Snmp getManager() {
        return Manager.instance.snmp4jSession;
    }

    public static TableUtils getTableUtils(int pduType) {
        switch (pduType) {
            case PDU.GETNEXT:
                return Manager.instance.getNextTableUtils;
            case PDU.GETBULK:
                return Manager.instance.getBulkTableUtils;
            default:
                return Manager.instance.getTableUtils;
        }
    }

    public static synchronized void cleanup() {
        Manager.instance.snmp4jSession.getUSM().removeAllUsers();
        Manager.instance.agents = new HashMap<>();
    }

    public static synchronized AbstractTarget getTarget(Query query)
            throws Exception {
        return Manager.instance.getTargetFor(query);
    }

    /**
     * Return or generate the target corresponding to snmp arguments contained
     * in the query.
     *
     * @param query the Query arguments
     * @return a snmp AbstractTarget
     * @throws Exception snmp bad config
     */
    public synchronized AbstractTarget getTargetFor(Query query)
            throws Exception {
        String targetId = query.get("target_id").asString();

        AbstractTarget target;
        target = this.agents.get(targetId);

        if (target != null) {
            return target;
        }

        String host = query.get("host").asString();
        int port = query.get("snmp_port").asInteger();
        String secLevel = query.get("snmp_seclevel").asString();
        String version = query.get("snmp_version").asString();
        int retries = query.get("snmp_retries").asInteger();
        int timeout = query.get("snmp_timeout").asInteger();

        Address address = GenericAddress.parse("udp:" + host + "/" + port);

        int secLevelConst = Manager.getSecLevel(secLevel);

        switch (version) {
            case "3":
                String secName = query.get("snmp_usm_user").asString();
                UserTarget targetV3 = new UserTarget();
                targetV3.setAddress(address);
                targetV3.setRetries(retries);
                targetV3.setTimeout(timeout);
                targetV3.setVersion(SnmpConstants.version3);
                targetV3.setSecurityLevel(secLevelConst);
                targetV3.setSecurityName(new OctetString(secName));
                target = targetV3;
                break;
            default:
                String community = query.get("snmp_community").asString();
                CommunityTarget communityTarget = new CommunityTarget();
                communityTarget.setCommunity(new OctetString(community));
                communityTarget.setAddress(address);
                communityTarget.setRetries(retries);
                communityTarget.setTimeout(timeout);
                if (version.equals("2c")) {
                    communityTarget.setVersion(SnmpConstants.version2c);
                } else {
                    communityTarget.setVersion(SnmpConstants.version1);
                }
                target = communityTarget;
        }

        if (target.getSecurityModel() != SecurityModel.SECURITY_MODEL_USM) {

            this.agents.put(targetId, target);
            return target;

        } else {

            OID authProtoOid = Manager.getAuthProto(
                    query.get("snmp_authproto").asString());
            OID privProtoOid = Manager.getPrivProto(
                    query.get("snmp_privproto").asString());

            OctetString uName = new OctetString(
                    query.get("snmp_usm_user").asString());
            OctetString authkey = new OctetString(
                    query.get("snmp_authkey").asString());
            OctetString privkey = new OctetString(
                    query.get("snmp_privkey").asString());

            UsmUser user = new UsmUser(
                    uName, authProtoOid, authkey, privProtoOid, privkey);

            OctetString username = user.getSecurityName();
            UsmUserEntry oldUser
                    = this.snmp4jSession.getUSM().getUserTable().getUser(username);

            if (oldUser == null) {
                this.snmp4jSession.getUSM().addUser(user);
                this.agents.put(targetId, target);
                return target;
            } else {
                if (oldUser.getUsmUser().toString().equals(user.toString())) {
                    // same users conf, ok
                    this.agents.put(targetId, target);
                    return target;
                }
                // TODO then replace old user with new one, use a thread lock
            }
        }
        throw new Exception("User name exists with differents credencials");
    }

    // utilities functions
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    // TODO test utf8 to byte might not work now
    public static byte[] getEngineId(String stringPath)
            throws Exception, Error {
        Path path = Paths.get(stringPath);
        if (Files.isRegularFile(path)) {

            byte[] engineIdDump = Files.readAllBytes(path);
            String engineIdHex = new String(engineIdDump, "UTF-8");
            return Manager.hexStringToBytes(engineIdHex); // return engine Id

        } else {

            byte[] engineId = MPv3.createLocalEngineID();
            String engineIdHex = Manager.bytesToHexString(engineId);
            byte[] engineIdDump = engineIdHex.getBytes("UTF-8");
            Files.write(path, engineIdDump);
            return engineId;

        }
    }

    private static byte[] hexStringToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHexString(byte[] bytes) throws Exception, Error {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(new String(hexChars).getBytes("UTF-8"), "UTF-8");
    }

    private static int getSecLevel(String constString) throws Exception {

        int secLevel;
        switch (constString) {
            case "authPriv":
                secLevel = SecurityLevel.AUTH_PRIV;
                break;
            case "authNoPriv":
                secLevel = SecurityLevel.AUTH_NOPRIV;
                break;
            case "noAuthNoPriv":
                secLevel = SecurityLevel.NOAUTH_NOPRIV;
                break;
            default:
                throw new Exception("No such secLevel!");
        }
        return secLevel;
    }

    private static OID getAuthProto(String constString) throws Exception {

        OID authProtoOid;
        switch (constString) {
            case "SHA":
                authProtoOid = AuthSHA.ID;
                break;
            case "MD5":
                authProtoOid = AuthMD5.ID;
                break;
            default:
                throw new Exception("unknown authentication protocol");
        }
        return authProtoOid;
    }

    private static OID getPrivProto(String constString) throws Exception {
        OID privProtoOid;
        switch (constString) {
            case "AES":
                privProtoOid = PrivAES128.ID;
                break;
            case "AES192":
                privProtoOid = PrivAES192.ID;
                break;
            case "AES256":
                privProtoOid = PrivAES256.ID;
                break;
            case "DES":
                privProtoOid = PrivDES.ID;
                break;
            case "3DES":
                privProtoOid = Priv3DES.ID;
                break;
            case "AES192_3DES":
                privProtoOid = PrivAES192With3DESKeyExtension.ID;
                break;
            case "AES256_3DES":
                privProtoOid = PrivAES256With3DESKeyExtension.ID;
                break;
            default:
                throw new Exception("unknown private protocol");
        }

        return privProtoOid;
    }
}
