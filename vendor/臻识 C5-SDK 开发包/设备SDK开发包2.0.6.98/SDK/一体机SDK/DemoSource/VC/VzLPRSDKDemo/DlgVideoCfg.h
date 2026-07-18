#pragma once
#include "afxcmn.h"
#include "SubDlgVideoCfg.h"
#include "SubVideoCfg2.h"

// CDlgVideoCfg dialog

class CDlgVideoCfg : public CDialog
{
	DECLARE_DYNAMIC(CDlgVideoCfg)

public:
	CDlgVideoCfg(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgVideoCfg();

// Dialog Data
	enum { IDD = IDD_DLG_VIDEO_CFG };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg void OnTcnSelchangeTabMain(NMHDR *pNMHDR, LRESULT *pResult);
	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );
    void SetBoardType(int type);
public:
	CTabCtrl m_tabMain;
	virtual BOOL OnInitDialog();

	static const int MAX_NUM_TAB_COUNT = 2;

	CSubDlgVideoCfg m_dlgSubVideoCfg1;
	CSubVideoCfg2 m_dlgSubVideoCfg2;

	CDialog* m_subDlgs[MAX_NUM_TAB_COUNT];
	int m_curSel;

	VzLPRClientHandle m_hLPRClient;
	int m_nBoardType;
};
