#pragma once
#include "afxcmn.h"
#include "afxwin.h"
#include "vzlprclientctrlctrl1.h"
#include "libcsv.h"


typedef enum _ReadState {
	READ_ID = 0,
	READ_PLATE,
	READ_ENABLE,
	READ_CREATE_TIME,
	READ_EXPIRES_TIME,
	READ_ENABLE_TIME_SEG,
	READ_TIME_SEG,
	READ_ENABLE_BLACK_LIST,
	READ_CUSTOMER_CODE,
	READ_CUSTOMER_ENCODE,
	READ_COMPLETE
}ReadState;

typedef struct
{
	unsigned	uVehicleID;				/**<车辆在数据库的ID*/
	char		strPlateID[16];			/**<车牌字符串*/
	unsigned	uCustomerID;			/**<客户在数据库的ID，与VZ_LPR_WLIST_CUSTOMER::uCustomerID对应*/
	unsigned	bEnable;				/**<该记录有效标记*/
	unsigned	bEnableTMEnable;		/**<是否开启生效时间*/
	char		strTMEnable[64];		/**生效时间*/
	unsigned	bEnableTMOverdule;		/**<是否开启过期时间*/
	char		strTMOverdule[64];		/**过期时间*/
	unsigned	bUsingTimeSeg;			/**<是否使用周期时间段*/
	unsigned	bAlarm;					/**<是否触发报警（黑名单记录）*/
	int			iColor;					/**<车辆颜色*/
	int			iPlateType;				/**<车牌类型*/
	char		strCode[32];			/**<车辆编码*/
	char		strComment[64];			/**<车辆编码*/
}
LPR_WLIST_VEHICLE;

// CDlgWlist dialog

class CDlgWlist : public CDialog
{
	DECLARE_DYNAMIC(CDlgWlist)

public:
	CDlgWlist(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgWlist();

// Dialog Data
	enum { IDD = IDD_DLG_WLIST };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	virtual BOOL OnInitDialog();
	void SetDeviceIP( CString strIP, int nPort, CString strUserName, CString strPwd );

	CListCtrl m_lstPlate;
	CEdit m_editPlate;
	CVzlprclientctrlctrl1 m_lprClientCtrl;
	afx_msg void OnClose();
	
	DECLARE_EVENTSINK_MAP()
	void OnLPRWlistInfoOutVzlprclientctrlctrl1(long lVehicleID, LPCTSTR strPlateID, BOOL bEnable, LPCTSTR strOverdule, BOOL bUsingTimeSeg, BOOL bAlarm, LPCTSTR strReserved, long lCustomerID, LPCTSTR strCustomerName, LPCTSTR strCustomerCode);
	afx_msg void OnBnClickedBtnAdd();
	afx_msg void OnBnClickedBtnEdit();
	afx_msg void OnBnClickedBtnDele();
	afx_msg void OnBnClickedBtnQuery();
	afx_msg void OnBnClickedBinClear();

	void LoadPlateList( CString strPlateKey );
	int GetSeleIndex( );

	void QueryByPage(int nPageIndex);

	void QueryWlist( );

	

private:
	int m_nLprHandle;
	CString m_strDeivceIP;
	int m_nPort;
	CString m_strUserName;
	CString m_strPwd;
public:
	void OnLPRWlistInfoOutExVzlprclientctrlctrl1(long lVehicleID, LPCTSTR strPlateID, BOOL bEnable, BOOL bEnableTM, BOOL bEnableOverdule, LPCTSTR strTMEnable, LPCTSTR strOverdule, BOOL bUsingTimeSeg, BOOL bAlarm, LPCTSTR strPlateCode, 
		LPCTSTR strPlateComment, long lCustomerID, LPCTSTR strCustomerName, LPCTSTR strCustomerCode);
	
	virtual BOOL PreTranslateMessage(MSG* pMsg);
	afx_msg void OnLvnItemchangedListPlate(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedBinBatchImport();
	afx_msg void OnBnClickedBtnFirstPage();
	afx_msg void OnBnClickedBtnPrevPage();
	afx_msg void OnBnClickedBtnNextPage();
	afx_msg void OnBnClickedBtnLastPage();

	void InsertWlistRecord(void *item_str, size_t size);

	void EndInsertRecord( );

	int m_nTotalCount;
	int m_nCurPage;
	int m_nPageCount;

	CString m_strPlate;
	CStatic m_lblPageInfo;
	CStatic m_lblCountInfo;

	LPR_WLIST_VEHICLE m_vehicle;

	int m_nParseIndex;
};
