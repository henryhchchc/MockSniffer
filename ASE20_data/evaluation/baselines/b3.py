def baseline3(X):
    return (
        X['ABS']
        | X['INT']
        | X['UINT']
        | (X['TDEP'] > X['TDEP'].mean())
        | (X['FIELD'] > X['FIELD'].mean())
        | ((X['UAPI']+X['TUAPI']) > (X['UAPI']+X['TUAPI']).mean())
        | (X['EXPCAT'] > 0)
        | (X['RBFA'] > 0)
        | (X['CONDCALL'] > 0)
        | (X['SYNC'] > X['SYNC'].mean())
        | (X['AFPR'] > 0)
    )
