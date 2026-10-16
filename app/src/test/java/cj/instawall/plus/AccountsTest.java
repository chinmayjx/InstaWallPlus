package cj.instawall.plus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

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
}
