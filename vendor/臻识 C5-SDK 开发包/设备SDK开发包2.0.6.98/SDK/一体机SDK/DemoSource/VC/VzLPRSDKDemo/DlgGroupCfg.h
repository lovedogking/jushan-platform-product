#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "afxcmn.h"

#include <vector>
#include <string>
using namespace std;

// CDlgGroupCfg dialog

class CDlgGroupCfg : public CDialog
{
	DECLARE_DYNAMIC(CDlgGroupCfg)

public:
	CDlgGroupCfg(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgGroupCfg();

// Dialog Data
	enum { IDD = IDD_DLG_GROUP_CFG };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

public:
	virtual BOOL OnInitDialog();
	void SetLPRHandle( VzLPRClientHandle hLPRClient );
	void SetDeviceIP( CString strIP );
	void MyExpandTree(HTREEITEM hTreeItem);

	void MyExpandAll(HTREEITEM hTreeItem);
	int GetSeleIndex( );

	HTREEITEM FindTreeItem( HTREEITEM firstNode, CString strText );

	HTREEITEM FindSubTreeItem( HTREEITEM parentNode, CString strText );

	void LoadGroupList( );

private:
	VzLPRClientHandle m_hLPRClient;

public:
	CString m_lblIP;
	CTreeCtrl m_groupTree;
	CListCtrl m_lstDevice;
	afx_msg void OnBnClickedBtnEdit();
	afx_msg void OnBnClickedBtnDele();

	VZ_OVZID_RESULT m_data;
	afx_msg void OnBnClickedBtnAdd();
};
