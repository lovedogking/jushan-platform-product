#pragma once
#include "afxwin.h"
#include<VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

// CDlgOSDSet dialog

class CDlgOSDSet : public CDialog
{
	DECLARE_DYNAMIC(CDlgOSDSet)

public:
	CDlgOSDSet(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgOSDSet();

// Dialog Data
	enum { IDD = IDD_DLG_OSD_SET };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	afx_msg void OnBnClickedOk();
	DECLARE_MESSAGE_MAP()

public:
	void SetOSDParam(VZ_LPRC_OSD_Param& osdParam);
	VZ_LPRC_OSD_Param* GetOSDParam( );
	void SetHwType( int nType, int board_version );
	void SetLPRHandle( VzLPRClientHandle lprHandle );

public:
	BOOL m_bShowDate;
	BOOL m_bShowTime;
	BOOL m_bShowText;
	CComboBox m_cmbDate;
	CComboBox m_cmbTime;
	
	int m_nDateX;
	int m_nDateY;
	int m_nTimeX;
	int m_nTimeY;
	int m_nTextX;
	int m_nTextY;
	CString m_strText;

	int m_hwType;
	int m_nBoardVersion;

private:
	VZ_LPRC_OSD_Param m_osdParam;
	VZ_LPRC_REALTIME_SHOW_PARAM m_showParam;

	VzLPRClientHandle m_lprHandle;

	BOOL m_bShowRealtime;
	BOOL m_bShowRule;
	BOOL m_bShowPos;
	BOOL m_bShowDistance;
};
