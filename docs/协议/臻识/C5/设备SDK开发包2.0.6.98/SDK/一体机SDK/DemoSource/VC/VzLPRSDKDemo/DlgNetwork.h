#pragma once
#include "afxcmn.h"
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
// CDlgNetwork dialog

class CDlgNetwork : public CDialog
{
	DECLARE_DYNAMIC(CDlgNetwork)

public:
	CDlgNetwork(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgNetwork();

// Dialog Data
	enum { IDD = IDD_DLG_NETWORK };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
	
public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );

	void LoadNetworkParam( );
	BOOL SaveNetworkParam( );

public:
	CIPAddressCtrl m_ipaddrIP;
	CIPAddressCtrl m_ipaddrMask;
	CIPAddressCtrl m_ipaddrGateway;
	CIPAddressCtrl m_ipaddrDNS;

	VzLPRClientHandle m_hLPRClient;
	virtual BOOL OnInitDialog();
	int m_nPort;
	afx_msg void OnBnClickedOk();
};
