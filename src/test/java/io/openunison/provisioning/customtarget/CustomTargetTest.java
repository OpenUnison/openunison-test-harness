package io.openunison.provisioning.customtarget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tremolosecurity.config.util.ConfigManager;
import com.tremolosecurity.openunison.OpenUnisonConfigManager;
import com.tremolosecurity.provisioning.core.Group;
import com.tremolosecurity.provisioning.core.User;
import com.tremolosecurity.provisioning.core.Workflow;
import com.tremolosecurity.provisioning.core.WorkflowImpl;
import com.tremolosecurity.proxy.util.ProxyConstants;
import com.tremolosecurity.saml.Attribute;
import com.tremolosecurity.server.GlobalEntries;
import com.tremolosecurity.provisioning.core.providers.JavaScriptTarget;
import com.tremolosecurity.provisioning.testing.LoadTargetFromYAML;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CustomTargetTest {


    private static ConfigManager cfgMgr;
	private static HashMap<String, Object> request;
    private static JavaScriptTarget jstarget;

    @BeforeAll
    public static void setup() throws Exception {

        request = new HashMap<String,Object>();
		
		request.put("APPROVAL_ID", 0);
		Workflow wf = new WorkflowImpl();
		request.put("WORKFLOW", wf);

        HashMap<String,Attribute> cfg = new HashMap<String,Attribute>();
        
        System.setProperty("com.tremolosecurity.unison.unisonServicePropsPath",System.getProperty("user.dir") + "/src/test/openunison/WEB-INF/unisonService.props");
        cfgMgr = new OpenUnisonConfigManager(System.getProperty("user.dir") + "/src/test/openunison/WEB-INF/autoidm-config.xml",null,"proxy",null);
		cfgMgr.initialize("proxy");
		GlobalEntries.getGlobalEntries().set(ProxyConstants.CONFIG_MANAGER,cfgMgr);
		
        // Create system/environment variables used by your target
        // accessed by using #[name] instead of the variable
        // DO NOT INCLUDE SECRET DATA!!!!
        HashMap<String,String> env = new HashMap<>();
		env.put("name","value");
		

        // Simulates pulling data from Kubernetes Secret objects
        // translates to the secretParams section of your target configuration
		Map<String,Map<String,String>> secrets = new HashMap<>();
		Map<String,String> orchestrSecretsSource = new HashMap<>();
		orchestrSecretsSource.put("somepasswordname","value");
		secrets.put("orchestra-secrets-source",orchestrSecretsSource);

        // load the target from YAML
		jstarget = (JavaScriptTarget) LoadTargetFromYAML.loadFromYAML(cfgMgr,System.getProperty("user.dir") + "/src/test/yaml/custom-target.yaml",env,secrets);

        
    }

    @AfterAll
	public static void tearDownAfterClass() throws Exception {
        jstarget.shutdown();
		
		
	}


    // start with user searches
    // make sure you can load a user and groups from your target
    @Test
    @DisplayName("Find a user")
    public void testFindUser() throws Exception {
        // specify which attributes will be returned
        Set<String> attributes = new HashSet<String>();
        attributes.add("username");
        attributes.add("first");
        attributes.add("last");
        attributes.add("email");

        // retrieve the user by their ID
        User userFromTarget = jstarget.findUser("mmosley",attributes,request);

        // make sure a user was returned
        validateUser(userFromTarget, "mmosley","Matt","Mosley","mmosley@nodomain.io","group1","group2","group3");

        // validate a user that doesn't exist returns null
        assertNull(jstarget.findUser("notarealuser",attributes,request));
    }

    private void validateUser(User userFromTarget,String username,String first,String last,String email,String... groups) {
        assertNotNull(userFromTarget);
        
        assertNotNull(userFromTarget.getAttribs().get("username"));
        assertEquals(username,userFromTarget.getAttribs().get("username").getValues().get(0));

        assertNotNull(userFromTarget.getAttribs().get("first"));
        assertEquals(first,userFromTarget.getAttribs().get("first").getValues().get(0));

        assertNotNull(userFromTarget.getAttribs().get("last"));
        assertEquals(last,userFromTarget.getAttribs().get("last").getValues().get(0));

        assertNotNull(userFromTarget.getAttribs().get("email"));
        assertEquals(email,userFromTarget.getAttribs().get("email").getValues().get(0));

        assertEquals(groups.length,userFromTarget.getGroups().size());

        Arrays.stream(groups).forEach(groupName -> assertTrue(userFromTarget.getGroups().contains(groupName)));

        
    }


    // next validate that you can create, update, and delete an account
    @Test
    @DisplayName("Create/Update/Delete")
    public void testCreateUpdateDelete() throws Exception {
        // specify which attributes will be returned
        Set<String> attributes = new HashSet<String>();
        attributes.add("username");
        attributes.add("first");
        attributes.add("last");
        attributes.add("email");

        // make sure the account doesn't already exist
        assertNull(jstarget.findUser("jjackson",attributes,request));

        // Create a new user
        User newUser = new User("jjackson");
        newUser.getAttribs().put("username",new Attribute("username","jjackson"));
        newUser.getAttribs().put("first",new Attribute("first","Jennifer"));
        newUser.getAttribs().put("last",new Attribute("last","Jackson"));
        newUser.getAttribs().put("email",new Attribute("email","jjackson@nodomain.io"));
        newUser.getGroups().add("group1");
        newUser.getGroups().add("group2");
        newUser.getGroups().add("group3");

        jstarget.createUser(newUser,attributes,request);

        User fromTarget = jstarget.findUser("jjackson",attributes,request);
        validateUser(fromTarget,"jjackson","Jennifer","Jackson","jjackson@nodomain.io","group1","group2","group3");

        // make some changes
        fromTarget.getAttribs().get("email").getValues().clear();
        fromTarget.getAttribs().get("email").getValues().add("jennifer.jackson@nodomain.io");

        fromTarget.getGroups().clear();
        fromTarget.getGroups().add("group1");
        fromTarget.getGroups().add("group3");
        fromTarget.getGroups().add("group4");

        // update the user in the target
        jstarget.syncUser(fromTarget,false,attributes,request);

        // validate the changes
        fromTarget = jstarget.findUser("jjackson",attributes,request);
        validateUser(fromTarget,"jjackson","Jennifer","Jackson","jennifer.jackson@nodomain.io","group1","group3","group4");

        // delete the user
        jstarget.deleteUser(fromTarget,request);
        // validate no longer in the target
        assertNull(jstarget.findUser("jjackson",attributes,request));



    }

    // next validate that you can create, update, and delete an account
    @Test
    @DisplayName("Create/Update/Delete From Sync")
    public void testCreateUpdateDeleteFromSync() throws Exception {
        // specify which attributes will be returned
        Set<String> attributes = new HashSet<String>();
        attributes.add("username");
        attributes.add("first");
        attributes.add("last");
        attributes.add("email");

        // make sure the account doesn't already exist
        assertNull(jstarget.findUser("jjackson",attributes,request));

        // Create a new user
        User newUser = new User("jjackson");
        newUser.getAttribs().put("username",new Attribute("username","jjackson"));
        newUser.getAttribs().put("first",new Attribute("first","Jennifer"));
        newUser.getAttribs().put("last",new Attribute("last","Jackson"));
        newUser.getAttribs().put("email",new Attribute("email","jjackson@nodomain.io"));
        newUser.getGroups().add("group1");
        newUser.getGroups().add("group2");
        newUser.getGroups().add("group3");

        jstarget.syncUser(newUser,true,attributes,request);

        User fromTarget = jstarget.findUser("jjackson",attributes,request);
        validateUser(fromTarget,"jjackson","Jennifer","Jackson","jjackson@nodomain.io","group1","group2","group3");

        // make some changes
        fromTarget.getAttribs().get("email").getValues().clear();
        fromTarget.getAttribs().get("email").getValues().add("jennifer.jackson@nodomain.io");

        fromTarget.getGroups().clear();
        fromTarget.getGroups().add("group1");
        fromTarget.getGroups().add("group3");
        fromTarget.getGroups().add("group4");

        // update the user in the target
        jstarget.syncUser(fromTarget,false,attributes,request);

        // validate the changes
        fromTarget = jstarget.findUser("jjackson",attributes,request);
        validateUser(fromTarget,"jjackson","Jennifer","Jackson","jennifer.jackson@nodomain.io","group1","group3","group4");

        // delete the user
        jstarget.deleteUser(fromTarget,request);
        // validate no longer in the target
        assertNull(jstarget.findUser("jjackson",attributes,request));



    }

    // test the user/group searches
    @Test
    @DisplayName("Lookup a user by their username")
    public void testFindUserByUsername() throws Exception {
        // specify which attributes will be returned
        Set<String> attributes = new HashSet<String>();
        attributes.add("username");
        attributes.add("first");
        attributes.add("last");
        attributes.add("email");

        // retrieve the user by their ID
        User userFromTarget = jstarget.lookupUserByLogin("mmosley");

        // make sure a user was returned
        validateUser(userFromTarget, "mmosley","Matt","Mosley","mmosley@nodomain.io","group1","group2","group3");

        // validate a user that doesn't exist returns null
        assertNull(jstarget.lookupUserByLogin("notarealuser"));
    }

    @Test
    @DisplayName("Lookup a user by their unique id")
    public void testFindUserByUniqueId() throws Exception {
        // specify which attributes will be returned
        Set<String> attributes = new HashSet<String>();
        attributes.add("username");
        attributes.add("first");
        attributes.add("last");
        attributes.add("email");

        // retrieve the user by their ID
        User userFromTarget = jstarget.lookupUserById("mmosley");

        // make sure a user was returned
        validateUser(userFromTarget, "mmosley","Matt","Mosley","mmosley@nodomain.io","group1","group2","group3");

        // validate a user that doesn't exist returns null
        assertNull(jstarget.lookupUserById("notarealuser"));
    }

    @Test
    @DisplayName("Lookup a group by its name")
    public void testFindGroupByName() throws Exception {
        Group group1 = jstarget.lookupGroupByName("group1");
        assertNotNull(group1);
        assertEquals("group1",group1.getName());
        assertEquals(1,group1.getMembers().size());
        assertTrue(group1.getMembers().contains("mmosley"));

        assertNull(jstarget.lookupGroupByName("notagroup"));
        
    }

    @Test
    @DisplayName("Lookup a group by its id")
    public void testFindGroupById() throws Exception {
        Group group1 = jstarget.lookupGroupById("group1");
        assertNotNull(group1);
        assertEquals("group1",group1.getName());
        assertEquals(1,group1.getMembers().size());
        assertTrue(group1.getMembers().contains("mmosley"));

        assertNull(jstarget.lookupGroupById("notagroup"));
        
    }

    @Test
    @DisplayName("Search for a user using an LDAP Search Filter")
    public void testFindUserByLDAPFilter() throws Exception {
        List<User> users = jstarget.searchUsers("(&(|(first=Matt)(last=Mosley))(objectClass=inetOrgPerson))");
        
        assertEquals(1,users.size());

        User user = users.get(0);
        validateUser(user, "mmosley","Matt","Mosley","mmosley@nodomain.io","group1","group2","group3");

        // test a search that will return all users
        users = jstarget.searchUsers("(&(objectClass=inetOrgPerson)(username=*))");
        
        assertEquals(1,users.size());

        user = users.get(0);
        validateUser(user, "mmosley","Matt","Mosley","mmosley@nodomain.io","group1","group2","group3");
    }

    @Test
    @DisplayName("Search for a group using an LDAP Search Filter")
    public void testFindGroupByLDAPFilter() throws Exception {
        List<Group> groups = jstarget.searchGroups("(&(objectClass=groupOfUniqueNames)(uniqueMember=mmosley))");

        assertEquals(3, groups.size());

        Group group1 = groups.get(0);
        assertNotNull(group1);
        assertEquals("group1",group1.getName());
        assertEquals(1,group1.getMembers().size());
        assertTrue(group1.getMembers().contains("mmosley"));

        Group group2 = groups.get(1);
        assertNotNull(group2);
        assertEquals("group2",group2.getName());
        assertEquals(1,group2.getMembers().size());
        assertTrue(group2.getMembers().contains("mmosley"));

        Group group3 = groups.get(2);
        assertNotNull(group3);
        assertEquals("group3",group3.getName());
        assertEquals(1,group3.getMembers().size());
        assertTrue(group3.getMembers().contains("mmosley"));

        // check for a search of all groups
        groups = jstarget.searchGroups("(&(objectClass=groupOfUniqueNames)(cn=*))");

        assertEquals(3, groups.size());

        group1 = groups.get(0);
        assertNotNull(group1);
        assertEquals("group1",group1.getName());
        assertEquals(1,group1.getMembers().size());
        assertTrue(group1.getMembers().contains("mmosley"));

        group2 = groups.get(1);
        assertNotNull(group2);
        assertEquals("group2",group2.getName());
        assertEquals(1,group2.getMembers().size());
        assertTrue(group2.getMembers().contains("mmosley"));

        group3 = groups.get(2);
        assertNotNull(group3);
        assertEquals("group3",group3.getName());
        assertEquals(1,group3.getMembers().size());
        assertTrue(group3.getMembers().contains("mmosley"));
    }


    
}