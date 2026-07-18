#pragma once
#include <VzLPRClientSDK_WhiteList.h>
#include "afxwin.h"
#include "afxcmn.h"

// SubDlgConfigWhiteList dialog


class SubDlgConfigWhiteList : public CDialog
{
	DECLARE_DYNAMIC(SubDlgConfigWhiteList)

public:
	SubDlgConfigWhiteList(CWnd* pParent = NULL);   // standard constructor
	virtual ~SubDlgConfigWhiteList();

	void SetHandle(VzLPRClientHandle handle);
	void iOnQueryLPRecord(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP, const VZ_LPR_WLIST_CUSTOMER *pCustomer);

// Dialog Data
	enum { IDD = IDD_DLG_SET_WLIST_1 };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	void iQueryLPRecPage();

	void iQueryCustomerPage();

	void iCleanLPRecEdit();

	void iCleanCustomerEdit();	

	void GetColorValue(int iValue,char* strValue);
	void GetColorIndex(int &iValue,const char* strValue);

	void GetPlateTypeValue(int iValue,char* strValue);
	void GetPlateTypeIndex(int &iValue,const char* strValue);

private:
	VzLPRClientHandle m_hLPRC;

	VZ_LPR_WLIST_VEHICLE *m_pLPRecords;
	unsigned m_uLPRecNum;

	unsigned m_uCurrLPRecPageNum;

	VZ_LPR_WLIST_CUSTOMER *m_pLPCustomers;
	unsigned m_uCustomerNum;

	unsigned m_uCurrCustomerPageNum;

	CListCtrl m_cListLPRec;
	CListCtrl m_cListCustomer;

	VZ_LPR_WLIST_SEARCH_WHERE m_LpSsearchWhere;
	VZ_LPR_WLIST_SEARCH_WHERE m_CusSsearchWhere;

#define MAX_ENUM_COUNT 20

	VZ_LPR_WLIST_ENUM_VALUE m_VehicleColorValues[MAX_ENUM_COUNT];
	int m_maxVehicleColorCount;

	VZ_LPR_WLIST_ENUM_VALUE m_PlateTypeValues[MAX_ENUM_COUNT];
	int m_maxPlateTypeCount;

	int m_lastVehicleListSel;
	int m_lastCustomerListSel;

	DECLARE_MESSAGE_MAP()
public:
	virtual BOOL OnInitDialog();
	virtual BOOL DestroyWindow();
	afx_msg void OnBnClickedBtnSaveLp();
	afx_msg void OnBnClickedBtnRefreshPageLp();
	afx_msg void OnBnClickedBtnNewLp();
	afx_msg void OnBnClickedBtnNewCustomer();
	afx_msg void OnBnClickedBtnSaveCustomer();
	afx_msg void OnBnClickedBtnRefreshPageCustomer();
	afx_msg void OnBnClickedBtnPrevPageCustomer();
	afx_msg void OnBnClickedBtnNextPageCustomer();
	afx_msg void OnBnClickedBtnDelCustomer();
	afx_msg void OnLvnItemchangedListCustomer(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnLvnItemchangedListLpRec(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedBtnSetCustId();
	afx_msg void OnBnClickedBtnPrevPageLp();
	afx_msg void OnBnClickedBtnNextPageLp();
	afx_msg void OnBnClickedBtnDelLp();
	afx_msg void OnBnClickedBtnLookupLp();
	afx_msg void OnBnClickedBtnLookupCust();
	void Refresh();
	afx_msg void OnEnChangeEditLookupLp();
	afx_msg void OnEnChangeEditLookupCust();
	CComboBox m_cmbVehicleUpdateOrDeleteBy;
	CComboBox m_cmbCustomerUpdateOrDeleteBy;
	CComboBox m_cmbVehicleColor;
	CComboBox m_cmbPlateType;
	afx_msg void OnBnClickedBtnLoadLpr();
	afx_msg void OnBnClickedBtnLoadCustomer();
	afx_msg void OnBnClickedButton1();
	virtual BOOL PreTranslateMessage(MSG* pMsg);
	afx_msg void OnBnClickedBtnDelBatch();
};
