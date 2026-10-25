package asia.carmix.market.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import asia.carmix.market.R;
import asia.carmix.market.Session;
import asia.carmix.market.api.Api;
import asia.carmix.market.model.User;
import asia.carmix.market.util.Task;

public class LoginActivity extends BaseActivity {

    private EditText mEmail, mPassword;
    private View mSubmit;
    private ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.login);

        mEmail = (EditText) findViewById(R.id.lg_email);
        mPassword = (EditText) findViewById(R.id.lg_password);
        mSubmit = findViewById(R.id.lg_submit);
        mProgress = (ProgressBar) findViewById(R.id.lg_progress);

        mSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });

        TextView toRegister = (TextView) findViewById(R.id.lg_to_register);
        toRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });
    }

    private void doLogin() {
        final String email = mEmail.getText().toString().trim();
        final String pwd = mPassword.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pwd)) {
            Toast.makeText(this, R.string.form_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        mSubmit.setEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        Task.run(new Task.Background<Api.AuthResult>() {
            @Override
            public Api.AuthResult run() throws Exception {
                return Api.login(LoginActivity.this, email, pwd);
            }
        }, new Task.Ui<Api.AuthResult>() {
            @Override
            public void onDone(Api.AuthResult result, Exception error) {
                mSubmit.setEnabled(true);
                mProgress.setVisibility(View.GONE);
                if (error != null) {
                    Toast.makeText(LoginActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Session.save(LoginActivity.this, result.token, result.user);
                Toast.makeText(LoginActivity.this,
                        getString(R.string.hello_user, result.user == null ? "" : result.user.username),
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
