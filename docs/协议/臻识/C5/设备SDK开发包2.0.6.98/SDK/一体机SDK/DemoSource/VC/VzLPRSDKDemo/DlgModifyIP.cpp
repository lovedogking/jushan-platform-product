// DlgModifyIP.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgModifyIP.h"
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include <string>

// CDlgModifyIP dialog

IMPLEMENT_DYNAMIC(CDlgModifyIP, CDialog)

CDlgModifyIP::CDlgModifyIP(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgModifyIP::IDD, pParent)
{
	m_nSL	= 0;
	m_nSH	= 0;
}

CDlgModifyIP::~CDlgModifyIP()
{
}

void CDlgModifyIP::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_IPADDR_IP, m_ipaddrIP);
	DDX_Control(pDX, IDC_IPADDR_MASK, m_ipaddrMask);
	DDX_Control(pDX, IDC_IPADDR_GATEWAY, m_ipaddrGateway);
}


BEGIN_MESSAGE_MAP(CDlgModifyIP, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgModifyIP::OnBnClickedOk)
END_MESSAGE_MAP()


// CDlgModifyIP message handlers

void CDlgModifyIP::OnBnClickedOk()
{
	if( SaveNetworkParam() )
	{
		OnOK();
	}
}

void CDlgModifyIP::SetNetParam(LPCTSTR strIP, unsigned int SL, unsigned int SH, LPCTSTR netmask, LPCTSTR gateway)
{
	m_strIP = strIP;
	m_nSL	= SL;
	m_nSH	= SH;

	m_strNetmask = netmask;
	m_strGateway = gateway;
}

BOOL CDlgModifyIP::SaveNetworkParam( )
{
	CString strIP;
	m_ipaddrIP.GetWindowText(strIP);

	CString strNetmask;
	m_ipaddrMask.GetWindowText(strNetmask);

	CString strGateway;
	m_ipaddrGateway.GetWindowText(strGateway);

	int ret = VzLPRClient_UpdateNetworkParam(m_nSH, m_nSL, strIP.GetBuffer(0), strGateway.GetBuffer(0), strNetmask.GetBuffer(0) );
	if ( ret == 2 )
	{
		MessageBox(_T("设备IP跟网关不在同一网段，请重新输入!"), _T("提示"), MB_OK );
		return FALSE;
	}
	else if( ret == -1 )
	{
		MessageBox(_T("修改网络参数失败，请重新输入!"), _T("提示"), MB_OK );
		return FALSE;
	}

	MessageBox(_T("修改网络参数成功"), _T("提示"), MB_OK );
	return TRUE;
}

BOOL CDlgModifyIP::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here
	if( m_strIP.GetLength() > 0 )
	{
		m_ipaddrIP.SetWindowText(m_strIP);
	}

	if( m_strNetmask.GetLength() > 0 )
	{
		m_ipaddrMask.SetWindowText(m_strNetmask);
	}
	else
	{
		m_ipaddrMask.SetWindowText("255.255.255.0");
	}

	if ( m_strGateway.GetLength() > 0 )
	{
		m_ipaddrGateway.SetWindowText(m_strGateway);
	}
	else
	{
		std::string sGateway = LPCSTR(m_strIP);
		int index = sGateway.find_last_of(".");
		if( index >= 0 )
		{
			sGateway = sGateway.substr(0, index);
			sGateway.append(".1");

			LPCSTR lsGetway = sGateway.c_str();
			m_ipaddrGateway.SetWindowText(lsGetway);
		}
	}

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}
