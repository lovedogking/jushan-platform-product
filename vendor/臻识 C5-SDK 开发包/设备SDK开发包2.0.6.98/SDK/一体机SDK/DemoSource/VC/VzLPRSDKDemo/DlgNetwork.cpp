// DlgNetwork.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgNetwork.h"


// CDlgNetwork dialog

IMPLEMENT_DYNAMIC(CDlgNetwork, CDialog)

CDlgNetwork::CDlgNetwork(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgNetwork::IDD, pParent)
	, m_nPort(0)
{
	m_hLPRClient = NULL;
}

CDlgNetwork::~CDlgNetwork()
{
}

void CDlgNetwork::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_IPADDR_IP, m_ipaddrIP);
	DDX_Control(pDX, IDC_IPADDR_MASK, m_ipaddrMask);
	DDX_Control(pDX, IDC_IPADDR_GATEWAY, m_ipaddrGateway);
	DDX_Control(pDX, IDC_IPADDR_DNS, m_ipaddrDNS);
	DDX_Text(pDX, IDC_EDIT1, m_nPort);
	DDV_MinMaxInt(pDX, m_nPort, 80, 80000);
}


BEGIN_MESSAGE_MAP(CDlgNetwork, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgNetwork::OnBnClickedOk)
END_MESSAGE_MAP()


// CDlgNetwork message handlers
void CDlgNetwork::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

BOOL CDlgNetwork::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here
	LoadNetworkParam( );

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

void CDlgNetwork::LoadNetworkParam( )
{
	if ( m_hLPRClient != NULL )
	{
		VZ_LPRC_NETCFG netCfg = {0};
		int ret = VzLPRClient_GetNetConfig( m_hLPRClient, &netCfg );
		if ( ret == 0 )
		{
			m_ipaddrIP.SetWindowText(netCfg.ipaddr);
			m_ipaddrMask.SetWindowText(netCfg.netmask);
			m_ipaddrGateway.SetWindowText(netCfg.gateway);
			m_ipaddrDNS.SetWindowText(netCfg.dns);

			m_nPort = netCfg.http_port;
			UpdateData(FALSE);
		}
	}
}

// 保存网络参数
BOOL CDlgNetwork::SaveNetworkParam( )
{
	UpdateData(TRUE);

	CString strIP;
	m_ipaddrIP.GetWindowText(strIP);

	CString strNetmask;
	m_ipaddrMask.GetWindowText(strNetmask);

	CString strGateway;
	m_ipaddrGateway.GetWindowText(strGateway);

	CString strDNS;
	m_ipaddrDNS.GetWindowText(strDNS);

	VZ_LPRC_NETCFG netCfg = {0};
	strncpy( netCfg.ipaddr, strIP.GetBuffer(0), sizeof(netCfg.ipaddr) );
	strncpy( netCfg.netmask, strNetmask.GetBuffer(0), sizeof(netCfg.netmask) );
	strncpy( netCfg.gateway, strGateway.GetBuffer(0), sizeof(netCfg.gateway) );
	strncpy( netCfg.dns, strDNS.GetBuffer(0), sizeof(netCfg.dns) );
	netCfg.http_port = m_nPort;

	int ret = VzLPRClient_SetNetConfig( m_hLPRClient, &netCfg );
	if ( ret != 0 )
	{
		MessageBox(_T("修改失败，请检查输入的网络参数是否正确"), _T("提示"), MB_OK );
		return FALSE;
	}

	MessageBox(_T("修改网络参数成功"), _T("提示"), MB_OK );
	return TRUE;
}
void CDlgNetwork::OnBnClickedOk()
{
	if( SaveNetworkParam() )
	{
		OnOK();
	}
}
