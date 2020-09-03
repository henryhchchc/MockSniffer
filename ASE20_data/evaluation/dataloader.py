dep_exclude_list = {
    'java.lang.Integer',
    'java.lang.Double',
    'java.lang.Float',
    'java.lang.Long',
    'java.lang.Boolean',
    'java.lang.Short',
    'java.lang.Character',
    'java.lang.Byte',

    'java.lang.Object',
    'java.lang.String',
    'java.lang.StringBuilder'
}

data_files = {
    'Hadoop': '../data_files/hadoop_0.csv',
    'Flink': '../data_files/flink_0.csv',
    'Hive': '../data_files/hive_0.csv',
    'Camel': '../data_files/camel_0.csv',
    'CXF': '../data_files/cxf_0.csv',
    'Druid': '../data_files/druid_0.csv',
    'HBase': '../data_files/hbase_0.csv',
    'Dubbo': '../data_files/dubbo_0.csv',
    'Oozie': '../data_files/oozie_0.csv',
    'Storm': '../data_files/storm_0.csv',
}

projects = list(data_files.keys())

feature_pp = [
    "DEP",
    "TDEP",
    "FIELD",
    "UAPI",
    "TUAPI",
    "SYNC",
    "CALLSITES",
    "AFPR",
    "RBFA",
    "EXPCAT",
    "CONDCALL"
]


def load_df(name, fn, is_scale):
    import pandas as pd
    from sklearn.preprocessing import robust_scale
    df = pd.read_csv(fn)
    df['PROJ'] = name
    df = df[~df['D'].isin(dep_exclude_list)]
    df = df[~df['D'].str.contains('$Proxy')]
    df = df[~df['TC'].str.contains('ITest')]
    df = df[df['L'] != 'spy']
    df['IS_MOCK'] = df['L'] != 'real'
    if is_scale:
        for f in feature_pp:
            df[f] = robust_scale(df[f].clip(
                upper=(df[f].mean()+df[f].std()*2)), with_centering=True)
            df[f] = df[f] - df[f].min()
    df = df.drop(['METHOD'], axis=1)
    return df


def get_raw_data(files, is_scale):
    import pandas as pd
    rd = pd.concat([
        load_df(n, f, is_scale) for (n, f) in files.items()
    ])
    return rd


def get_dataset(files, is_scale):
    rd = get_raw_data(files, is_scale)
    return rd.drop_duplicates()
