// DlgTcpTran.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgTcpTran.h"
#define WM_RECV_TCP_TRAN (WM_USER + 212)

void __stdcall gVZLPRC_TCPSERVER_RECV_CALLBACK(VzLPRClientHandle handle, const char* pData,int dataLen,void *pUserData)
{
	HWND hwnd = (HWND)pUserData;
	if( hwnd != NULL && dataLen > 0 )
	{
		char *tcp_data = new char[dataLen+1];
		memset(tcp_data, 0, dataLen+1);
		memcpy(tcp_data, pData, dataLen);

		PostMessage(hwnd, WM_RECV_TCP_TRAN, (WPARAM)tcp_data, (LPARAM)NULL);
	}
}

// CDlgTcpTran dialog

IMPLEMENT_DYNAMIC(CDlgTcpTran, CDialog)

CDlgTcpTran::CDlgTcpTran(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgTcpTran::IDD, pParent)
	, m_strTcpData(_T(""))
	, m_strTcpRecv(_T(""))
{
	m_hLPRClient = NULL;
}

CDlgTcpTran::~CDlgTcpTran()
{
}

void CDlgTcpTran::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT_TCP_SEND, m_strTcpData);
	DDX_Text(pDX, IDC_EDIT_TCP_RECV, m_strTcpRecv);
}


BEGIN_MESSAGE_MAP(CDlgTcpTran, CDialog)
	ON_BN_CLICKED(IDC_BTN_SEND_DATA, &CDlgTcpTran::OnBnClickedBtnSendData)
	ON_MESSAGE(WM_RECV_TCP_TRAN, &CDlgTcpTran::OnShowTcpDataMsg)
END_MESSAGE_MAP()

BOOL CDlgTcpTran::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here

	if( m_hLPRClient != NULL )
	{
		VzLPRClient_RegisterTcpServerChannal(m_hLPRClient);
		VzLPRClient_SetTcpServerRecvCallBack(m_hLPRClient, gVZLPRC_TCPSERVER_RECV_CALLBACK, this->GetSafeHwnd());
	}

	return TRUE;
}

// CDlgTcpTran message handlers
void CDlgTcpTran::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}

// 发送tcp透明数据
void CDlgTcpTran::OnBnClickedBtnSendData()
{
	UpdateData(TRUE);

	if ( m_hLPRClient != NULL )
	{
		int ret = VzLPRClient_SendTcpServerData(m_hLPRClient, m_strTcpData.GetBuffer(0), m_strTcpData.GetLength() + 1);
	}
}

LRESULT CDlgTcpTran::OnShowTcpDataMsg(WPARAM w,LPARAM l)
{
	char* tcp_data = (char *)w;
	if( tcp_data != NULL )
	{
		m_strTcpRecv = tcp_data;
		UpdateData(FALSE);

		delete[] tcp_data;
		tcp_data = NULL;
	}

	return 0;
}