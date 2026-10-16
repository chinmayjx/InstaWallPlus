package cj.instawall.plus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class AccountsTest {
    @Test
    public void oldSingleRedditAccountIsMigrated() throws Exception {
        Accounts a = new Accounts(new JSONObject().put("reddit", new JSONObject().put("username", "me").put("cookie", "c=1")));
        assertEquals("me", a.redditUser());
        assertEquals("c=1", a.redditCookie("me"));
        assertEquals(Arrays.asList("me"), a.redditUsers());
    }

    @Test
    public void loggingInAnotherAccountMakesItActiveAndKeepsTheFirst() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("one", "c=1");
        a.loginReddit("two", "c=2");
        assertEquals("two", a.redditUser());
        assertEquals(2, a.redditUsers().size());
        a.switchReddit("one");
        assertEquals("one", a.redditUser());
        assertEquals("c=1", a.redditCookie("one"));
    }

    @Test
    public void switchingToAnUnknownAccountDoesNothing() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("one", "c=1");
        a.switchReddit("nobody");
        assertEquals("one", a.redditUser());
    }

    @Test
    public void loggingOutTheActiveAccountActivatesAnother() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("one", "c=1");
        a.loginReddit("two", "c=2");
        a.logoutReddit("two");
        assertEquals("one", a.redditUser());
        a.logoutReddit("one");
        assertNull(a.redditUser());
        assertFalse(a.redditUsers().contains("one"));
    }

    @Test
    public void refreshedCookiesAreStored() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("one", "c=1");
        a.updateRedditCookie("one", "c=2");
        assertEquals("c=2", a.redditCookie("one"));
        a.updateRedditCookie("nobody", "c=3");
        assertNull(a.redditCookie("nobody"));
    }

    @Test
    public void everyAccountIsListedInstagramFirst() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("red", "c=2");
        a.loginInstagram("insta", "c=1", null);
        a.loginReddit("other", "c=3");
        assertEquals(Arrays.asList("insta", "red", "other"), users(a.allAccounts()));
    }

    @Test
    public void loggingInAgainUpdatesTheCookie() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("red", "c=1");
        a.loginInstagram("insta", "c=2", null);
        a.loginReddit("red", "c=3");
        a.loginInstagram("insta", "c=4", null);
        assertEquals("c=3", a.redditCookie("red"));
        assertEquals("c=4", a.instagramCookie("insta"));
        assertEquals(2, a.allAccounts().size());
    }

    @Test
    public void loggingOutOfInstagramPicksAnotherBrowserAccount() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginInstagram("one", "c=1", null);
        a.loginInstagram("two", "c=2", null);
        a.logoutInstagram("two");
        assertEquals("one", a.instagramUser());
        a.logoutInstagram("one");
        assertNull(a.instagramUser());
        assertTrue(a.allAccounts().isEmpty());
    }

    private static java.util.List<String> users(java.util.List<String[]> accounts) {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String[] x : accounts) l.add(x[1]);
        return l;
    }
}
