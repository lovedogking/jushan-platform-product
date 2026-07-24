// DlgEncrypt.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgEncrypt.h"

// CDlgEncrypt dialog

IMPLEMENT_DYNAMIC(CDlgEncrypt, CDialog)

CDlgEncrypt::CDlgEncrypt(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgEncrypt::IDD, pParent)
	, m_strUserPwd(_T(""))
	, m_strUserPwd2(_T(""))
	, m_strNewPwd(_T(""))
	, m_strNewrPwd2(_T(""))
	, m_strPwdText(_T(""))
{
	m_hLPRClient = NULL;
	m_strPwdText = "用户密码：";
	m_pDev		 = NULL;
}

CDlgEncrypt::~CDlgEncrypt()
{
}

void CDlgEncrypt::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT_USER_PWD, m_strUserPwd);
	DDV_MaxChars(pDX, m_strUserPwd, 25);
	DDX_Text(pDX, IDC_EDIT_USER_PWD2, m_strUserPwd2);
	DDV_MaxChars(pDX, m_strUserPwd2, 25);
	DDX_Text(pDX, IDC_EDIT_NEW_PWD, m_strNewPwd);
	DDV_MaxChars(pDX, m_strNewPwd, 25);
	DDX_Text(pDX, IDC_EDIT_NEW_PWD2, m_strNewrPwd2);
	DDV_MaxChars(pDX, m_strNewrPwd2, 25);
	DDX_Control(pDX, IDC_CMB_TYPE, m_cmbType);
	DDX_Text(pDX, IDC_PWD_TEXT, m_strPwdText);
}


BEGIN_MESSAGE_MAP(CDlgEncrypt, CDialog)
	ON_BN_CLICKED(IDC_BTN_SAVE, &CDlgEncrypt::OnBnClickedBtnSave)
	ON_BN_CLICKED(IDC_RADIO_RESET, &CDlgEncrypt::OnBnClickedRadioReset)
	ON_BN_CLICKED(IDC_RADIO_MODIFY, &CDlgEncrypt::OnBnClickedRadioModify)
	ON_BN_CLICKED(IDOK, &CDlgEncrypt::OnBnClickedOk)
	ON_CBN_SELCHANGE(IDC_CMB_TYPE, &CDlgEncrypt::OnCbnSelchangeCmbType)
END_MESSAGE_MAP()


// CDlgEncrypt message handlers

BOOL CDlgEncrypt::OnInitDialog()
{
	CDialog::OnInitDialog();

	if ( m_hLPRClient != NULL )
	{
		VZ_LPRC_ACTIVE_ENCRYPT data = {0};
		int ret = VzLPRClient_GetEMS(m_hLPRClient, &data);
		if ( data.uSize > 0 )
		{
			for ( int i = 0; i < data.uSize; i++ )
			{
				m_cmbType.AddString(data.oEncrpty[i].sName);
			}
			
			m_cmbType.SetCurSel(data.uActiveID);
		}
	}

	((CButton *)GetDlgItem(IDC_RADIO_MODIFY))->SetCheck(TRUE);
	
	return TRUE;
}

void CDlgEncrypt::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgEncrypt::SetDeviceInfo(DeviceLPR *pDev)
{
	m_pDev = pDev;
}

// 保存加密方式
void CDlgEncrypt::OnBnClickedBtnSave()
{
	UpdateData(TRUE);

	int nCurSel = m_cmbType.GetCurSel( );

	// 无加密
	if ( m_strUserPwd.GetLength() == 0 )
	{
		MessageBox( _T("请输入用户密码！"), _T("提示"), MB_OK );
		return;
	}

	string userPwd = "";
	int value = VzLPRClient_SetEncrypt(m_hLPRClient, m_strUserPwd.GetBuffer(0), nCurSel );
	if ( value != 0 )
	{
		MessageBox( _T("设置加密方式失败，请检查密码是否正确！"), _T("提示"), MB_OK );

		GetDlgItem(IDC_EDIT_USER_PWD)->SetWindowText(userPwd.c_str());
		return;
	}

	m_pDev->SetUserPwd(m_strUserPwd.GetBuffer(0));

	MessageBox( _T("设置加密方式成功！"), _T("提示"), MB_OK );

	GetDlgItem(IDC_EDIT_USER_PWD)->SetWindowText(userPwd.c_str());

	return;
}

void CDlgEncrypt::OnBnClickedRadioReset()
{
	m_strPwdText = "主密码：";
	UpdateData(FALSE);
}

void CDlgEncrypt::OnBnClickedRadioModify()
{
	m_strPwdText = "用户密码：";
	UpdateData(FALSE);
}

void CDlgEncrypt::OnBnClickedOk()
{
	UpdateData(TRUE);

	CString strUserPwd = m_strUserPwd2;

	// 无加密
	if ( strUserPwd.GetLength() == 0 )
	{
		MessageBox( _T("请输入用户密码！"), _T("提示"), MB_OK );
		return;
	}

	CString strNewPwd = m_strNewPwd;
	if( strNewPwd.GetLength() == 0 )
	{
		MessageBox( _T("请输入新密码！"), _T("提示"), MB_OK );
		return;
	}

	CString strRePwd = m_strNewrPwd2;
	if( strRePwd.GetLength() == 0 )
	{
		MessageBox( _T("请输入确认密码！"), _T("提示"), MB_OK );
		return;
	}

	if ( strNewPwd != strRePwd )
	{
		MessageBox( _T("两次输入密码不一致，请重新输入！"), _T("提示"), MB_OK );
		return;
	}

	int value = -1;

	// 修改密码
	int check = ((CButton *)GetDlgItem(IDC_RADIO_MODIFY))->GetCheck();
	if ( check == 1 )
	{
		value = VzLPRClient_ChangeEncryptKey( m_hLPRClient, strUserPwd.GetBuffer(0), strNewPwd.GetBuffer(0) );
	}
	else
	{
		// 重置密码
		value = VzLPRClient_ResetEncryptKey( m_hLPRClient, strUserPwd.GetBuffer(0), strNewPwd.GetBuffer(0) );
	}

	if ( value != 0 )
	{
		MessageBox( _T("设置密码失败，请检查原始密码是否正确！"), _T("提示"), MB_OK );
		return;
	}

	MessageBox( _T("设置密码成功！"), _T("提示"), MB_OK );

	OnOK();
}
void CDlgEncrypt::OnCbnSelchangeCmbType()
{
	// TODO: Add your control notification handler code here
	string userPwd = "";
	GetDlgItem(IDC_EDIT_USER_PWD)->SetWindowText(userPwd.c_str());
}
