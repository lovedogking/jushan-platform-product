// DlgActiveStatus.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgActiveStatus.h"


// CDlgActiveStatus dialog

IMPLEMENT_DYNAMIC(CDlgActiveStatus, CDialog)

CDlgActiveStatus::CDlgActiveStatus(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgActiveStatus::IDD, pParent)
	, m_bUseTime(FALSE)
	, m_strUserPwd(_T(""))
	, m_dtDate(COleDateTime::GetCurrentTime())
	, m_dtTime(COleDateTime::GetCurrentTime())
	, m_strLeaveTime(_T(""))
{
	m_hLPRClient = NULL;
}

CDlgActiveStatus::~CDlgActiveStatus()
{
}

void CDlgActiveStatus::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Check(pDX, IDC_CHK_TIME, m_bUseTime);
	DDX_Text(pDX, IDC_EDIT_USER_PWD, m_strUserPwd);
	DDX_DateTimeCtrl(pDX, IDC_DT_DATE, m_dtDate);
	DDX_DateTimeCtrl(pDX, IDC_DT_TIME, m_dtTime);
	DDX_Text(pDX, IDC_EDIT_LEAVE_TIME, m_strLeaveTime);
}


BEGIN_MESSAGE_MAP(CDlgActiveStatus, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgActiveStatus::OnBnClickedOk)
	ON_BN_CLICKED(IDC_CHK_TIME, &CDlgActiveStatus::OnBnClickedChkTime)
END_MESSAGE_MAP()


// CDlgActiveStatus message handlers

void CDlgActiveStatus::OnBnClickedOk()
{
	UpdateData(TRUE);

	if( m_strUserPwd.GetLength() == 0 )
	{
		MessageBox("请输入用户密码! ");
		return;
	}

	int status = m_bUseTime ? 1 : 0;

	COleDateTime dtNow		= COleDateTime::GetCurrentTime();

	char szExpireTime[128] = {0};
	sprintf_s( szExpireTime, sizeof(szExpireTime), "%d-%02d-%02d %02d:%02d:%02d", m_dtDate.GetYear(), m_dtDate.GetMonth(), m_dtDate.GetDay(),
		m_dtTime.GetHour(), m_dtTime.GetMinute(), m_dtTime.GetSecond() );

	COleDateTime dtExpire;
	dtExpire.ParseDateTime(szExpireTime);

	int times = 0;

	COleDateTimeSpan span = dtExpire - dtNow;
	if( span.GetTotalSeconds() <= 0 )
	{
		if( status )
		{
			MessageBox("过期时间不能小于当前时间! ");
			return;
		}
	}
	else
	{
		times  = span.GetTotalSeconds();
	}

	int ret = VzLPRClient_SetDeviceActiveStatus(m_hLPRClient, status, times, m_strUserPwd.GetBuffer(0));
	if( ret == 0 )
	{
		MessageBox("设置成功! ");
		OnOK();
	}
	else
	{
		MessageBox("设置失败，请检查用户密码是否正确! ");
	}
}

BOOL CDlgActiveStatus::OnInitDialog()
{
	CDialog::OnInitDialog();

	int status = -1, times = -1;
	int ret = VzLPRClient_GetDeviceActiveStatus(m_hLPRClient, &status, &times);
	if ( ret == 0 )
	{
		if ( status == 1 )
		{
			m_bUseTime = TRUE;
		}

		COleDateTime dtNow = COleDateTime::GetCurrentTime();
		CTimeSpan span(times);

		char szLeaveTime[128] = {0};

		if( span.GetDays() > 0 )
		{
			char szDays[32] = {0};
			sprintf(szDays, "%d天", span.GetDays());
			strcat(szLeaveTime, szDays);
		}


		sprintf(szLeaveTime, "%s %d时 %d分 %d 秒", szLeaveTime, span.GetHours(), span.GetMinutes(), span.GetSeconds());
		m_strLeaveTime = szLeaveTime;

		COleDateTimeSpan value;
		value.SetDateTimeSpan(span.GetDays(), span.GetHours(), span.GetMinutes(), span.GetSeconds());
		int day = span.GetDays();
		COleDateTime dtExpire = dtNow + value;

		m_dtDate = dtExpire;
		m_dtTime = dtExpire;

		if( !m_bUseTime )
		{
			((CDateTimeCtrl *)GetDlgItem(IDC_DT_DATE))->EnableWindow(FALSE);
			((CDateTimeCtrl *)GetDlgItem(IDC_DT_TIME))->EnableWindow(FALSE);
		}

		UpdateData(FALSE);
	}

	return TRUE;
}

void CDlgActiveStatus::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}

void CDlgActiveStatus::OnBnClickedChkTime()
{
	UpdateData(TRUE);

	((CDateTimeCtrl *)GetDlgItem(IDC_DT_DATE))->EnableWindow(m_bUseTime);
	((CDateTimeCtrl *)GetDlgItem(IDC_DT_TIME))->EnableWindow(m_bUseTime);
}
