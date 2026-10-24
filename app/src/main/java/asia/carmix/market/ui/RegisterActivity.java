package asia.carmix.market.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
import asia.carmix.market.util.Task;

/**
 * 注册：邮箱 + 验证码（邮件下发）+ 用户名 + 密码（≥6位）。
 */
public class RegisterActivity extends Activity {

    private EditText mEmail, mCode, mUsername, mPassword;
    private TextView mSendCode;
    private View mSubmit;
    private ProgressBar mProgress;
    private final Handler mHandler = new Handler();
    private int mCountdown = 0;
    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            mCountdown--;
            if (mCountdown > 0) {
                mSendCode.setText(getString(R.string.resend_after, mCountdown));
                mSendCode.setEnabled(false);
                mHandler.postDelayed(this, 1000);
            } else {
                mSendCode.setText(R.string.send_code);
                mSendCode.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.register);

        mEmail = (EditText) findViewById(R.id.rg_email);
        mCode = (EditText) findViewById(R.id.rg_code);
        mUsername = (EditText) findViewById(R.id.rg_username);
        mPassword = (EditText) findViewById(R.id.rg_password);
        mSendCode = (TextView) findViewById(R.id.rg_send_code);
        mSubmit = findViewById(R.id.rg_submit);
        mProgress = (ProgressBar) findViewById(R.id.rg_progress);

        mSendCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCode();
            }
        });
        mSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doRegister();
            }
        });
    }

    private void sendCode() {
        final String email = mEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, R.string.email_required, Toast.LENGTH_SHORT).show();
            return;
        }
        mSendCode.setEnabled(false);
        Task.run(new Task.Background<Object>() {
            @Override
            public Object run() throws Exception {
                Api.sendVerificationCode(RegisterActivity.this, email);
                return null;
            }
        }, new Task.Ui<Object>() {
            @Override
            public void onDone(Object result, Exception error) {
                if (error != null) {
                    mSendCode.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(RegisterActivity.this, R.string.code_sent, Toast.LENGTH_SHORT).show();
                mCountdown = 60;
                mTick.run();
            }
        });
    }

    private void doRegister() {
        final String email = mEmail.getText().toString().trim();
        final String code = mCode.getText().toString().trim();
        final String username = mUsername.getText().toString().trim();
        final String pwd = mPassword.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(code)
                || TextUtils.isEmpty(username) || TextUtils.isEmpty(pwd)) {
            Toast.makeText(this, R.string.form_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pwd.length() < 6) {
            Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        mSubmit.setEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        Task.run(new Task.Background<Api.AuthResult>() {
            @Override
            public Api.AuthResult run() throws Exception {
                return Api.register(RegisterActivity.this, email, username, pwd, code);
            }
        }, new Task.Ui<Api.AuthResult>() {
            @Override
            public void onDone(Api.AuthResult result, Exception error) {
                mSubmit.setEnabled(true);
                mProgress.setVisibility(View.GONE);
                if (error != null) {
                    Toast.makeText(RegisterActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Session.save(RegisterActivity.this, result.token, result.user);
                Toast.makeText(RegisterActivity.this, R.string.register_ok, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mTick);
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
