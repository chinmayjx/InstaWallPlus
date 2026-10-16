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
    public void normalModeUsesEveryAccountNotMarkedNsfw() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginInstagram("insta", "c=1", null);
        a.loginReddit("red", "c=2");
        a.loginReddit("spicy", "c=3");
        a.setNsfw("reddit", "spicy", true);
        assertFalse(a.nsfwMode());
        assertEquals(Arrays.asList("insta", "red"), users(a.activeAccounts()));
        assertEquals(3, a.allAccounts().size());
    }

    @Test
    public void nsfwModeUsesOnlyNsfwAccounts() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginInstagram("insta", "c=1", null);
        a.loginInstagram("night", "c=2", null);
        a.loginReddit("spicy", "c=3");
        a.setNsfw("instagram", "night", true);
        a.setNsfw("reddit", "spicy", true);
        a.setNsfwMode(true);
        assertEquals(Arrays.asList("night", "spicy"), users(a.activeAccounts()));
        a.setNsfwMode(false);
        assertEquals(Arrays.asList("insta"), users(a.activeAccounts()));
    }

    @Test
    public void loggingInAgainKeepsTheNsfwMark() throws Exception {
        Accounts a = new Accounts(new JSONObject());
        a.loginReddit("spicy", "c=1");
        a.loginInstagram("night", "c=2", null);
        a.setNsfw("reddit", "spicy", true);
        a.setNsfw("instagram", "night", true);
        a.loginReddit("spicy", "c=3");
        a.loginInstagram("night", "c=4", null);
        assertTrue(a.isNsfw("reddit", "spicy"));
        assertTrue(a.isNsfw("instagram", "night"));
        assertEquals("c=3", a.redditCookie("spicy"));
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
