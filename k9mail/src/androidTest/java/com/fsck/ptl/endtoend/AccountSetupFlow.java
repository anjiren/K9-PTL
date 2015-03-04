package com.fsck.ptl.endtoend;

import com.fsck.ptl.endtoend.framework.AccountForTest;
import com.fsck.ptl.endtoend.framework.ApplicationState;
import com.fsck.ptl.endtoend.framework.StubMailServer;
import com.fsck.ptl.endtoend.framework.UserForImap;
import com.fsck.ptl.endtoend.pages.AccountOptionsPage;
import com.fsck.ptl.endtoend.pages.AccountSetupNamesPage;
import com.fsck.ptl.endtoend.pages.AccountSetupPage;
import com.fsck.ptl.endtoend.pages.AccountTypePage;
import com.fsck.ptl.endtoend.pages.AccountsPage;
import com.fsck.ptl.endtoend.pages.IncomingServerSettingsPage;
import com.fsck.ptl.endtoend.pages.OutgoingServerSettingsPage;
import com.fsck.ptl.endtoend.pages.WelcomeMessagePage;
import com.fsck.ptl.mail.ConnectionSecurity;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Encapsulated the steps required to set up a new mail account.
 */
public class AccountSetupFlow {

    static final String ACCOUNT_NAME = "sendAndReceiveTestName";

    public AccountsPage setupAccountFromWelcomePage(WelcomeMessagePage welcomeMessagePage) {
        AccountSetupPage accountSetupPage = welcomeMessagePage.clickNext();
        return setupAccountFromSetupNewAccountActivity(accountSetupPage);
    }

    public AccountsPage setupAccountFromAccountsPage(AccountsPage accountPage) {
        AccountSetupPage accountSetupPage = accountPage.clickAddNewAccount();
        return setupAccountFromSetupNewAccountActivity(accountSetupPage);
    }

    public AccountsPage setupAccountFromSetupNewAccountActivity(AccountSetupPage accountSetupPage) {
        AccountTypePage accountTypePage = fillInCredentialsAndClickManualSetup(accountSetupPage);

        IncomingServerSettingsPage incoming = accountTypePage.clickImap();


        StubMailServer stubMailServer = ApplicationState.getInstance().stubMailServer;

        OutgoingServerSettingsPage outgoing = setupIncomingServerAndClickNext(incoming, stubMailServer);

        AccountOptionsPage accountOptionsPage = setupOutgoingServerAndClickNext(outgoing, stubMailServer);

        AccountSetupNamesPage accountSetupNamesPage = accountOptionsPage.clickNext();

        String accountDescription = tempAccountName();
        accountSetupNamesPage.inputAccountDescription(accountDescription);
        accountSetupNamesPage.inputAccountName(ACCOUNT_NAME);

        AccountsPage accountsPage = accountSetupNamesPage.clickDone();

        accountsPage.assertAccountExists(accountDescription);

        ApplicationState.getInstance().accounts.add(new AccountForTest(ACCOUNT_NAME, accountDescription, stubMailServer));

        return accountsPage;
    }


    private String tempAccountName() {
        return "sendAndReceiveTest-" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date());
    }

    private AccountTypePage fillInCredentialsAndClickManualSetup(AccountSetupPage page) {
        return page
                .inputEmailAddress(UserForImap.TEST_USER.emailAddress)
                .inputPassword(UserForImap.TEST_USER.password)
                .clickManualSetup();
    }

    private AccountOptionsPage setupOutgoingServerAndClickNext(OutgoingServerSettingsPage page, StubMailServer stubMailServer) {
        return page
                .inputSmtpServer(stubMailServer.getSmtpBindAddress())
                .inputSmtpSecurity(ConnectionSecurity.NONE)
                .inputPort(stubMailServer.getSmtpPort())
                .inputRequireSignIn(false)
                .clickNext();
    }

    private OutgoingServerSettingsPage setupIncomingServerAndClickNext(IncomingServerSettingsPage page, StubMailServer stubMailServer) {
        return page
                .inputImapServer(stubMailServer.getImapBindAddress())
                .inputImapSecurity(ConnectionSecurity.NONE)
                .inputPort(stubMailServer.getImapPort())
                .inputUsername(UserForImap.TEST_USER.loginUsername)
                .clickNext();
    }
}
